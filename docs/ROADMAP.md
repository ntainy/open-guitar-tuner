# Roadmap (after the MVP)

Companion to `PLAN.md`. The MVP is done (main @ 644554a, verified with an acoustic guitar through the phone mic).
Ground rule for everything below: anything that touches detection is built, played on the phone, and tuned from
the frame log (`adb logcat -s TunerFrames:V`) before it counts as done.

Sizes: S = a session or less · M = a few sessions · L = multi-wave, needs art or a data-model change.

## Feel
- **Haptics** (S, requested) — slider step ticks (`SegmentFrequentTick`), confirm on a tuned mark and on "all set",
  toggle feel on AUTO and pin/unpin, a rate-limited tick when the needle enters the band; one switch in Settings.
  In-tune tick is a ViewModel one-shot event, like the toast.
- **Scrolling pitch trace** (M) — last ~6 s of cents as a scrolling path under the gauge; optional.
- **Reference tone on long-press** (S) — synthesized pluck via `AudioTrack`; capture pauses while it plays.
- **Landscape / tablet layout** (S) — gauge and note left, headstock right.

## Chromatic (M, requested)
A "Chromatic" entry at the top of the tunings list. Engine flag skips the resolver: target = nearest MIDI note of
the smoothed pitch (octave guard kept), cents relative to it. Tune screen swaps the headstock for a note strip.
Alternative (a third position on the AUTO control) rejected: the tunings list is where you say what you tune.

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
- **Release build** (S) — R8 with serialization keep rules, local keystore (out of git), versionCode bumps,
  `assembleRelease` in CLAUDE.md; share the APK by file.
- **USB verification with the MP-3** (S) — first hardware contact; checklist + the two logcat tags.
- **USB tips in Settings › Input** (S).

## Data
- **Import/export tunings** (S) — share sheet out, document picker in, id-safe merge; per-tuning share.
- **Capo offset per tuning** (S). **Favourites and reorder** (S).

## Suggested order
1. M6 · Feel and ship: haptics, landscape, release build, USB tips, first shareable APK.
2. M7 · Chromatic, reference tone, trace.
3. M8 · Instruments (subagent wave): model + migration, presets, editor/list, art + anchors, bass detector pass, then 12-string.
4. M9 · Data: import/export, capo, favourites.

## Considered and skipped
Bluetooth mics (latency/codec), widget or Wear OS, Play Store listing, smarter string prediction (ambiguous by
nature; pinning is the escape hatch), cloud sync.

## Open decisions
Instrument priority (default 7-string + bass first) · art source (default vectors now) · chromatic entry point
(default tunings list) · start with M6 (default yes).
