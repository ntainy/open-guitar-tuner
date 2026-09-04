# DSP chain

Everything in `:dsp` is pure Kotlin/JVM (`dev.ntainy.guitar_tuner.dsp`). The chain the engine wires up:

```
AudioSource.samples()  48 kHz mono float chunks of any size
    │
    ▼
RingBufferFrameAssembler(frameSize = 4096, hopSize = 2048)      one frame every 42.7 ms, 50 % overlap
    │  FloatArray(4096), reused between callbacks
    ▼
YinPitchDetector(sampleRate = 48000, frameSize = 4096, 40..1200 Hz, threshold = 0.15)
    │  PitchEstimate(frequencyHz, confidence, rms) or null
    ▼
MedianEmaSmoother()                                              display frequency in Hz or null
    │
    ▼
HysteresisTargetResolver(switchMarginCents = 30.0, framesToSwitch = 3)   index of the string being tuned
    │
    ▼
TunerState (centsOff = NoteMath.cents(smoothedHz, targetHz))
```

Per-frame budget: hop 2048 at 48 kHz gives 23.4 readings per second; the analysis window is 85 ms long, so the
displayed value lags the string by roughly half a window plus the smoother's settling time (a few frames).

All four classes preallocate their buffers; nothing is allocated per call except the `PitchEstimate` the detector
returns. None of them is thread-safe: create one set per analysis thread and call them from that thread only.
`reset()` on each clears its state; the engine should call all three resets when capture restarts or the tuning
changes.

## Frame assembly

`RingBufferFrameAssembler` keeps a ring of `frameSize` samples. Chunks of any size are appended; when `frameSize`
samples have arrived the first frame is emitted, then one more every `hopSize` samples, each holding the most recent
`frameSize` samples unwrapped in chronological order. The callback receives the same scratch array every time, valid
only during the callback. `hopSize` must be in `1..frameSize`. `reset()` discards the partial frame.

## Pitch detection: YIN

`YinPitchDetector` implements de Cheveigné & Kawahara's YIN (2002) in the time domain.

| Parameter | Default | Why |
|---|---|---|
| `sampleRate` | 48 000 | what `AudioRecord` delivers for both the mic and the USB interface |
| `frameSize` | 4096 (85 ms) | long enough for 2.4 periods of a 40 Hz tone in the integration window; short enough to follow a tuning peg |
| `minFrequencyHz` | 40 | lowest fundamental searched; sets `maxLag = ceil(48000 / 40) = 1200` |
| `maxFrequencyHz` | 1200 | highest fundamental searched; sets `minLag = floor(48000 / 1200) = 40` |
| `windowSize` | `frameSize − maxLag` = 2896 | every lag integrates the same 2896 samples, so `d(τ)` is comparable across lags |
| `threshold` | 0.15 | YIN's absolute threshold on the normalised difference; the paper's sweet spot, resists octave errors |
| `fallbackThreshold` | 0.30 | global minimum accepted when nothing crosses 0.15 (confidence then 0.70–0.85); `0.0` disables it |
| `silenceRms` | 0.003 (−50 dBFS) | AC RMS gate; below it `detect` returns `null` before doing any work |

Steps per frame:

1. **Level and DC.** One pass computes the mean and mean square; the AC RMS is reported as `PitchEstimate.rms` and
   gates silence. The frame is copied to a `DoubleArray` with the mean removed.
2. **Difference function** `d(τ) = Σ_{j<W} (x_j − x_{j+τ})²`, accumulated in `Double` with four independent
   accumulators. Lags are computed lazily in increasing order.
3. **Cumulative mean normalised difference** `d'(τ) = d(τ) · τ / Σ_{k≤τ} d(k)`, computed from `τ = 1` (not from
   `minLag`) so the normalisation is the standard one and a tone at exactly `maxFrequencyHz` still dips.
4. **Absolute threshold.** The search starts at `minLag`. At the first lag with `d' < threshold` the detector keeps
   scanning while `d'` stays below the threshold and takes the lowest point of that region. This differs from the
   textbook "descend to the first local minimum": on a sine-like valley with noise the textbook rule stops at a
   ripple and can be off by tens of cents (measured +106 cents at −10 dB SNR; the region minimum gives −6). Because
   the scan stops as soon as the dip has been passed, a frame containing an E4 costs about a tenth of a frame of
   white noise.
   If no lag dips below the threshold, the global minimum of `d'` over `[minLag, maxLag]` is accepted when it is
   below `fallbackThreshold`, otherwise the frame is unvoiced (`null`). The fallback keeps a decaying note readable
   for longer; it is also the only path where octave errors were observed (once in 20 seeds at −6 dB SNR).
5. **Parabolic interpolation** of the raw `d` through the chosen lag and its neighbours gives the fractional period;
   `frequencyHz = sampleRate / period`, `confidence = 1 − d'(τ)`.

The difference function is `O(maxLag · windowSize)` = 3.5 M multiply-adds in the worst case (unvoiced frame, full
scan). Measured on the development machine's JVM (Apple silicon, JDK 25, after warm-up, 100 frames each):

| Frame content | Lags computed | Average per frame |
|---|---|---|
| E4 329.6 Hz plucked string | ≈ 175 | 0.08 ms |
| A2 110 Hz plucked string | ≈ 470 | 0.24 ms |
| 45 Hz sine | ≈ 1090 | 0.60 ms |
| white noise (full scan + fallback) | 1200 | 0.65 ms |

`PerformanceTest` asserts under 5 ms (deliberately loose) and prints the numbers.

## Smoothing

`MedianEmaSmoother` turns per-frame estimates into a stable display value. It works in cents relative to A4 so
octaves are equal distances. Defaults, all constructor parameters:

| Parameter | Default | Effect |
|---|---|---|
| `minConfidence` | 0.5 | estimates below this count as empty frames |
| `nullFramesToReset` | 4 | the last value is held for 3 empty frames (~130 ms); the 4th resets and returns `null` |
| `jumpCents` | 80 | a reading further than this from the smoothed value is an outlier |
| `jumpFrames` | 3 | the 3rd consecutive outlier is trusted: the smoother jumps to the median of the run |
| `medianWindow` | 3 | accepted readings are median-filtered before the EMA |
| `alpha` | 0.45 | EMA coefficient; a 20-cent step is 95 % followed after 5 frames |

Net behaviour: a single octave glitch never shows, a real string change shows on the third frame (~130 ms), jitter
standard deviation drops to roughly 40 % of the input, and "no signal" appears after ~170 ms of silence.

## Target resolution

`HysteresisTargetResolver` chooses which string the reading belongs to in AUTO mode. The first call takes the
nearest target in cents (lowest index on a tie, so the two D3 strings of Double Daddy resolve to index 2). After
that the current string is kept unless another string is closer by more than `switchMarginCents` (30) on
`framesToSwitch` (3) consecutive calls. A pitch that wobbles around the midpoint between two strings therefore never
flickers, while a clear move to the next string is followed after three readings. `reset()` when the tuning changes.

## Accuracy (from `YinPitchDetectorTest`)

Test signal: `SyntheticSignals.pluckedString` — 8 partials with amplitude `1/k^1.2`, the 2nd partial at 1.3× the
fundamental (to provoke octave-up errors), inharmonic partials `f_k = k·f0·sqrt(1 + B·k²)`, white noise 30 dB below
the fundamental, peak 0.5. Every MIDI note from B1 (35) to G4 (67) at −40, −10, 0, +10 and +40 cents: 165 frames per
row. Error is `NoteMath.cents(detected, f0)`.

| Inharmonicity B | mean error | min | max | max abs error | min confidence |
|---|---|---|---|---|---|
| 0 (harmonic) | +0.003 | −0.098 | +0.094 | 0.098 | 0.999 |
| 5e-5 (typical plain string) | +0.494 | +0.375 | +0.593 | 0.593 | 0.999 |
| 1e-4 | +0.984 | +0.846 | +1.084 | 1.084 | 0.999 |
| 2e-4 (stiff string) | +1.963 | +1.760 | +2.094 | 2.094 | 0.998 |

Standard strings at B = 5e-5, error in cents per offset:

| String | −40 | −10 | 0 | +10 | +40 |
|---|---|---|---|---|---|
| E2 | +0.47 | +0.51 | +0.56 | +0.47 | +0.43 |
| A2 | +0.53 | +0.43 | +0.49 | +0.51 | +0.53 |
| D3 | +0.56 | +0.46 | +0.56 | +0.51 | +0.51 |
| G3 | +0.49 | +0.47 | +0.51 | +0.45 | +0.48 |
| B3 | +0.52 | +0.46 | +0.54 | +0.47 | +0.50 |
| E4 | +0.46 | +0.51 | +0.49 | +0.50 | +0.50 |

Other checks: pure sines at 82.41 and 329.63 Hz within 0.5 cent (measured < 0.1); a 45 Hz sine is detected within
1 cent; a plucked string with the 2nd partial at 3× the fundamental still reads the fundamental; silence, a −54 dBFS
tone, DC offsets and white noise (5 seeds) return `null`; confidence for clean tones is above 0.99.

## Known limitations

- **Inharmonicity reads sharp.** The rows above show the detector's own error is ±0.1 cent; the rest is physics.
  A stiff string's partials are progressively sharp of `k·f0`, so the waveform is not periodic at `1/f0` and its
  best-fit period is shorter. The shift is about `+1 cent per 1e-4 of B` with this partial spectrum, uniform across
  the fretboard. Every period- or autocorrelation-based tuner (GuitarTuna included) reads the same thing, and the
  ear's pitch of an inharmonic tone also sits sharp of `f0`, so this is not corrected. A first-order de-emphasis
  low-pass before YIN was tried and rejected: it broadens the valley and made errors worse (up to 13 cents), not
  better. Consequence for the test suite: the 1-cent assertion runs at B = 5e-5; at B = 2e-4 the tests assert a
  consistent +1.5…+2.5 cent shift with < 0.5 cent spread.
- **Sine-like input and noise.** Rich partials sharpen the valley of `d(τ)`; a lone fundamental makes it broad, and
  noise then moves the interpolated minimum. Per-frame error on a 110 Hz sine: 0.1 cent at −40 dB SNR, 1 cent at
  −30 dB, 4 cents at −20 dB, 25 cents at −10 dB (a plucked spectrum at the same SNRs: 0.02, 0.09, 0.9, 4 cents).
  The smoother's median and EMA average this down, and a guitar through the USB interface is far above −30 dB.
- **Octave errors** were only seen through the fallback path (global minimum between 0.15 and 0.30) at SNR below
  about −6 dB. Such frames carry confidence ≤ 0.85; raise `MedianEmaSmoother(minConfidence = 0.85)` or construct the
  detector with `fallbackThreshold = 0.0` to drop them entirely.
- **Amplitude decay inside a frame** tilts the valley slightly. Measured effect with a half-life of 0.3 s (a fast
  guitar decay): max 0.22 cents; 0.1 s: 0.9 cents. Irrelevant at guitar decay rates.
- **Range.** Fundamentals outside 40–1200 Hz are not searched. `frameSize` must be at least `2 · maxLag`
  (2400 samples for 40 Hz), so a 2048-sample frame needs `minFrequencyHz ≥ 47`.
- **Extreme 2nd-harmonic dominance.** A `d'` dip below 0.15 at half the period requires the odd partials to hold
  under about 7 % of the energy; with the 2nd partial at 3× the fundamental the detector still reads `f0`, but a
  string plucked exactly at its midpoint node through a pickup that also notches the fundamental could trip it.

## What to tune if real strings misbehave

Change the constructor arguments in `AppContainer`; nothing in the algorithms is hard-coded.

| Symptom | Knob |
|---|---|
| Readings drop out while a note sustains, confidence hovering ~0.8 | `threshold` 0.15 → 0.20; or keep `threshold` and rely on `fallbackThreshold` (already 0.30) |
| Octave-up readings on the low strings | `threshold` 0.15 → 0.10 (the T/2 dip must get deeper to pass); check `fallbackThreshold` first |
| Octave errors flashing at the tail of a note | `MedianEmaSmoother(minConfidence = 0.85)` or `YinPitchDetector(fallbackThreshold = 0.0)` |
| Needle too nervous | `alpha` 0.45 → 0.30, `medianWindow` 3 → 5 (adds ~1 frame of lag) |
| Needle too sluggish | `alpha` 0.45 → 0.60, `medianWindow` 5 → 3, `jumpFrames` 3 → 2 |
| Display lags the peg | `hopSize` 2048 → 1024 (47 readings/s, 2× CPU, still < 1.5 ms per frame) |
| Low tunings below ~50 Hz shaky | `frameSize` 4096 → 8192 with `hopSize` 2048 (longer window, more periods) |
| AUTO flickers between two strings | `switchMarginCents` 30 → 50, `framesToSwitch` 3 → 5 |
| AUTO slow to pick the new string | `framesToSwitch` 3 → 2 |
| Readings appear from room noise | `silenceRms` 0.003 → 0.01 (−40 dBFS) |
| Quiet pickup never registers | `silenceRms` 0.003 → 0.001 |

## Stages added after the first real-guitar test (M5)

The synthetic tests were clean; a Galaxy S23 Ultra microphone and an acoustic guitar were not. Three things
showed up in the per-frame log (`adb logcat -s TunerFrames:V`, debug builds) and each got a small, testable stage:

| Problem seen on the phone | Stage | Where |
|---|---|---|
| A 41–55 Hz room hum at 1/10 of a note's level read as "E2, 900 cents flat" between plucks | `NoiseGate`: a frame must be 2× louder than the quietest second of the last eight (floor ≥ 0.004, threshold capped at 0.02 so a continuous tone never gates itself) | engine, before the smoother |
| Decaying strings handed YIN their 2nd/3rd overtone (392 Hz on the G string → "E4 +300") | `Harmonics` + `HysteresisTargetResolver.resolveMatch`: every string is scored with its best overtone fold (penalties 20/45/60 cents for ×2/×3/×4); a best score above 600 cents is "not a string" | resolver |
| Octave-*down* errors in the decay with high confidence (A2 → 54.6 Hz, G3 → 48.9 Hz) | `OctaveGuard`: while a note rings, a reading within 40 cents of one or two octaves of the last accepted pitch is folded back onto it | engine, before the smoother |
| The first ~0.3 s of every pluck is sharp and unstable (readings 20–200 cents off at 0.1 s), then the string drifts flat by 2–3 cents over two seconds | `OnsetDetector` (chunk 1.8× louder than the decayed peak) → hold the reading for 7 frames (≈300 ms), then restart the smoother and the octave guard from the settled pitch; smoother now `medianWindow = 5`, `alpha = 0.3` | engine |
| A pluck's scattered attack readings (215, 228, 61 Hz) were accepted as a "consistent" jump | `MedianEmaSmoother`: the `jumpFrames` outliers must agree within `jumpSpreadCents` (40) before the smoother follows them; a median more than `snapCents` (25) away snaps instead of easing | smoother |
| A fresh high E showed as "low E, fourth overtone" for three frames | every onset calls `resolver.reset()`; 0.5 s of silence does too | engine |

Measured on the phone over comparable runs (six strings plucked, then one string tuned): string switches fell
from 46 to 6, 127 octave errors were folded back, 224 attack frames were held, and no sub-60 Hz frame reached
the display. The remaining 2–3 cent flat drift over a pluck's decay is the string
itself (amplitude-dependent tension), not the detector; read the gauge about half a second after the pluck.
