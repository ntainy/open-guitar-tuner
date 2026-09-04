# Roadmap (after the MVP)

Companion to `PLAN.md`. The MVP is done (main @ 644554a, verified with an acoustic guitar through the phone mic).
Ground rule for everything below: anything that touches detection is built, played on the phone, and tuned from
the frame log (`adb logcat -s TunerFrames:V`) before it counts as done.

Sizes: S = a session or less · M = a few sessions · L = multi-wave, needs art or a data-model change.

## Feel — done
- **Haptics** ✅ — `ui/haptics/TunerHaptics.kt` is the whole vocabulary in one place, gated by one switch in
  Settings › Display. It drives the **vibrator directly** rather than Compose's `performHapticFeedback`: that route
  is gated by Android's system-wide "touch feedback" setting, which was off on the test phone, so every haptic was
  silently dropped (`dumpsys vibrator_manager` showed `ignored_for_settings`). A tuner's haptics answer "did that
  string land?" while you are looking at the guitar, so the app's own switch is the authority. Slider and stepper ticks, toggle feel on AUTO/pin/unpin and every switch and segmented row,
  confirm on a tuned mark, a firmer nudge on "all set", long-press on the reference tone. The in-tune tick is a
  ViewModel one-shot rate-limited to 600 ms so a needle on the edge of the band cannot buzz.
- **Material 3 Expressive** ✅ — `GuitarTunerTheme` is a `MaterialExpressiveTheme` with `MotionScheme.expressive()`,
  so every Material component springs the way the hand-drawn needle does. This needs material3 **1.5.0-alpha27**,
  held ahead of the Compose BOM in `libs.versions.toml`: the whole expressive API is compiled `internal` in the
  1.4.0 the BOM pins. `TunerShapes` moves the shape scale up a notch and string buttons morph circle → squared while
  targeted. A two-position AUTO/MANUAL segmented control was tried and reverted — the string bubbles already pin and
  unpin, so a pill that wide bought nothing and pushed the app name out of the header.
- **Scrolling pitch trace** ✅ — `ui/tuner/PitchTrace.kt`, last 6 s under the gauge, **on by default**: it shipped
  switched off and was simply never found. Drawn on its own panel ground, with rules at ±25 cents and the readings
  inset from the edges so a value clamped at ±50 is still a whole line. Sampled on a
  40 ms timer rather than from `engine.state`: a StateFlow stops emitting when the pitch stops changing, and a trace
  that freezes in silence lies about time. Silence is drawn as a gap, not a slope. Dropped on screens under 420 dp.
- **Reference tone on long-press** ✅ — `dsp/PluckedToneSynth` renders the note (7 partials, per-partial damping,
  raised-cosine attack and release, normalised to 0.85 so nothing clips) and `audio/ReferenceTonePlayer` plays it
  through `AudioTrack`. Capture stops for the note and resumes only after the last overlapping one, and only if the
  screen is still up, so a note finishing after the user leaves cannot quietly reopen the microphone.
- **Landscape / tablet layout** ✅ — `TunerBody` splits side by side once the screen is wider than it is tall.

## Chromatic — done ✅
A "Chromatic" entry leading the tunings list, carried in `PresetTunings.all` so selecting, persisting and resolving
it need no special machinery; `Tuning.isChromatic` is the flag. The engine skips the resolver and the overtone
folding entirely: the target is the nearest MIDI note of the smoothed pitch, cents measured against it, published as
`TunerState.chromaticMidi`. The octave guard is kept, so a decaying note does not drop an octave; nothing folds an
overtone back onto a fundamental, because if the ear hears the octave the display should say so.

The Tune screen swaps the headstock for `ui/tuner/NoteStrip.kt` — the twelve pitch classes with the one being heard
lit and squared off the way a targeted string button is — and hides AUTO and "Start over", neither of which means
anything without strings. There are no tuned marks: a chromatic reading is a measurement, not a task with an end.
The breadcrumb drops the "Guitar 6-string ›" prefix, since chromatic is not an instrument's tuning.

The alternative (a third position on the AUTO control) stayed rejected: the tunings list is where you say what you
are tuning.

## Instruments (L, requested)
- **7-string, 4/5-string bass, ukulele** — `STRING_COUNT` → `Instrument.stringCount`, `Tuning.instrument`,
  presets per instrument, instrument filter chips, per-instrument headstock art + anchors (7-string 4+3 / 7-in-line,
  bass 2+2 / 4-in-line, 5-string 3+2, ukulele 2+2). Detector: 5-string low B is 30.9 Hz → `minFrequencyHz` 28
  (fits the 4096 frame); expect a bass pass on the gate and fold rules; bass is far better over USB than a phone mic.
  Existing custom tunings migrate as six-string guitar (JSON `version` bump).
- **12-string** — twelve strings shown as six pairs in a 6+6 headstock; octave pairs are exactly the overtone case,
  do last so it is a small delta.
- **Art** — illustrations from Nikita, or vector headstocks in the app's style now and swapped later.

## Ship
- **Release build** ✅ — R8 on for release, signing config from env vars → `local.properties` → unsigned, keystore
  out of git, `versionCode`/`versionName` overridable from CI. See `docs/RELEASING.md`. Release APK is ~3.05 MB.
- **CI/CD** ✅ — `.github/workflows/ci.yml` runs the suite and uploads a debug APK on every push and PR;
  `release.yml` builds a signed APK on a `v*` tag and publishes a GitHub Release. The release job listens only to
  tag pushes, so fork PRs cannot reach the signing secrets.
- **USB tips in Settings › Input** ✅ — connect before opening, what "Prefer USB" does, what to check when nothing
  appears, and a note that amp-modelling interfaces send the modelled signal.
- **USB verification with the MP-3** (S) — still the one thing that has never met hardware. First plug-in with your
  friend: confirm it appears in the input sheet, is auto-selected, tunes an electric cleanly, and that unplugging
  falls back to the mic. Grep `adb logcat -s TunerEngine AudioInputMonitor`.

## Data
- **Import/export tunings** (S) — share sheet out, document picker in, id-safe merge; per-tuning share.
- **Capo offset per tuning** (S). **Favourites and reorder** (S).

## Suggested order
1. ~~M6 · Feel and ship~~ ✅ haptics, expressive motion, landscape, release build + CI/CD, USB tips.
2. M7 · ~~reference tone~~ ✅, ~~trace~~ ✅ — **chromatic mode is the remaining piece.**
3. M8 · Instruments (subagent wave): model + migration, presets, editor/list, art + anchors, bass detector pass, then 12-string.
4. M9 · Data: import/export, capo, favourites.

## Open questions raised by the work so far
- **material3 is on an alpha.** 1.5.0-alpha27 is pinned ahead of the Compose BOM for the expressive API. Alphas
  churn; re-check the theme call and the Slider rendering on each bump, and drop the override once 1.5.0 is stable
  and the BOM catches up.
- **Headstock art.** The app ships the original supplied illustrations again. A set of original vector headstocks
  was drawn (3+3 and 6-in-line, graphite & brass, with their own peg anchors) and is parked in
  `art/vector-headstocks/` with the generator that produced it — it is licence-clean and ready if the raster art
  ever has to go, and it is the obvious starting point for the other instruments in M8.
- **R8 and the persisted enums.** The keep rules in `src/main/keepRules/rules.keep` were measured to be a no-op
  today: R8 declines to rename the constants because the generated serializers reference `values()`. They are kept
  as insurance, because the failure they guard against is silent — renamed constants mean `settings.json` no longer
  parses, and `ReplaceFileCorruptionHandler` then deletes every custom tuning. No unit test can catch it, since
  tests never run through R8. Worth a manual pass on a signed release build after any AGP or R8 upgrade.

## Considered and skipped
Bluetooth mics (latency/codec), widget or Wear OS, Play Store listing, smarter string prediction (ambiguous by
nature; pinning is the escape hatch), cloud sync.

## Open decisions
Instrument priority (default 7-string + bass first) · art source (default vectors now) · chromatic entry point
(default tunings list) · start with M6 (default yes).
