# OpenGuitarTuner

A free and open source Android guitar tuner with the GuitarTuna interaction and none of its limits: you see your six strings on
a headstock, the app tells you which string it hears and how many cents you are off, and you can keep as many
custom tunings as you like. Listens through the phone microphone or through a USB audio interface such as the
NUX Mighty Plug Pro MP-3.

Built for one phone running Android 16, so there is no compatibility baggage: minSdk 36, Kotlin, Jetpack Compose,
Material 3, and a small hand-written YIN pitch detector.

## Status

Working and in use, unit-tested (232 tests across `:dsp` and `:app`) and verified on a Galaxy S23 Ultra with an
acoustic guitar through the phone microphone. The USB input path is implemented and unit-tested but has not yet
been tried with a real interface. See `docs/ROADMAP.md` for what comes next, `docs/PLAN.md` for how the MVP was built, `docs/ARCHITECTURE.md` for how the
pieces fit, `docs/DSP.md` for the pitch detector, `docs/TUNINGS.md` for the preset list, and `CLAUDE.md` for build
commands and conventions.

## Features

- Six-string preview on original headstock art, 3+3 or 6-in-line, with the active tuning post ringed in brass.
- Cents gauge (±50) with a springing needle, "Tune up / Tune down / In tune" hint, and tuned-string marks.
- AUTO string detection with hysteresis, or tap a string to pin it (tap it again to hand back to AUTO); a toast when all six are in tune.
- Haptics throughout: ticks as sliders step, a nudge when a string earns its mark, a firmer one when all six land.
  Driven by the vibrator directly, so the app's own switch decides rather than Android's system touch-feedback setting.
- Long-press a string to hear its target pitch as a synthesized pluck; capture pauses while it sounds.
- Optional scrolling pitch trace: the last six seconds under the gauge, so you can watch a note settle.
- Unlimited custom tunings with a full editor; 23 presets grouped as Standard, Power, Transposed, Open, Extras.
- Input from the built-in microphone or a USB audio interface, chosen automatically or by hand.
- Reference pitch 415–466 Hz, tolerance 1–10 cents, sharps or flats, dark/light theme, keep-screen-on.
- Side-by-side layout in landscape and on tablets; debug builds add a synthetic "Test tone" input.

## Building

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :dsp:test :app:testDebugUnitTest assembleDebug   # what CI runs
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` runs R8 and signs the APK when a keystore is configured; with none configured it still succeeds
and produces an unsigned APK. Every push and pull request runs the tests and builds a debug APK
(`.github/workflows/ci.yml`); pushing a `v*` tag builds and publishes a signed release
(`.github/workflows/release.yml`). See `docs/RELEASING.md` for the keystore setup and how to cut a release.

## Layout

| Path | What |
|---|---|
| `dsp/` | Pure-JVM DSP: note math, YIN pitch detection, smoothing, string resolution. Unit-tested. |
| `app/` | The Android app: audio capture, persistence, Compose UI. |
| `art/` | Headstock illustrations, an original vector set in reserve, and the font licence. |
| `docs/` | Plan, architecture, DSP notes, preset tuning list, release process. |

## Licence

Copyright (C) 2026 ntainy

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see
<https://www.gnu.org/licenses/>.

The full text is in [`LICENSE`](LICENSE); the source lives at <https://github.com/ntainy/open-guitar-tuner>.

### Bundled font

Bricolage Grotesque is bundled under the SIL Open Font License 1.1, not the GPL — its licence is
`art/OFL-BricolageGrotesque.txt` and it stays in force for the font files. The two coexist without friction: the OFL
is a permissive licence that places no restriction on the licensing of the software a font ships alongside, and it
requires only that the font keep its own licence and reserved name. Redistributing OpenGuitarTuner therefore means
honouring the GPL for the code and the OFL for the font, with no conflicting obligations between them.
