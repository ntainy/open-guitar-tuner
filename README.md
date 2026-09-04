# GuitarTuner

A personal Android guitar tuner with the GuitarTuna interaction and none of its limits: you see your six strings on
a headstock, the app tells you which string it hears and how many cents you are off, and you can keep as many
custom tunings as you like. Listens through the phone microphone or through a USB audio interface such as the
NUX Mighty Plug Pro MP-3.

Built for one phone running Android 16, so there is no compatibility baggage: minSdk 36, Kotlin, Jetpack Compose,
Material 3, and a small hand-written YIN pitch detector.

## Status

MVP complete, unit-tested (204 tests across `:dsp` and `:app`) and verified on a Galaxy S23 Ultra with an acoustic
guitar through the phone microphone. The USB input path is implemented and unit-tested but has not yet been tried
with a real interface. See `docs/PLAN.md` for the roadmap, `docs/ARCHITECTURE.md` for how the
pieces fit, `docs/DSP.md` for the pitch detector, `docs/TUNINGS.md` for the preset list, and `CLAUDE.md` for build
commands and conventions.

## Features

- Six-string preview on your own headstock art, 3+3 or 6-in-line, with the active tuning post ringed in brass.
- Cents gauge (±50) with a springing needle, "Tune up / Tune down / In tune" hint, and tuned-string marks.
- AUTO string detection with hysteresis, or tap a string to pin it (tap it again to hand back to AUTO); a toast when all six are in tune.
- Unlimited custom tunings with a full editor; 23 presets grouped as Standard, Power, Transposed, Open, Extras.
- Input from the built-in microphone or a USB audio interface, chosen automatically or by hand.
- Reference pitch 415–466 Hz, tolerance 1–10 cents, sharps or flats, dark/light theme, keep-screen-on.
- Debug builds add a synthetic "Test tone" input for exercising the UI without a guitar.

## Building

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Layout

| Path | What |
|---|---|
| `dsp/` | Pure-JVM DSP: note math, YIN pitch detection, smoothing, string resolution. Unit-tested. |
| `app/` | The Android app: audio capture, persistence, Compose UI. |
| `art/` | Original headstock illustrations and font licence. |
| `docs/` | Plan, architecture, DSP notes, preset tuning list. |

## Licence

Personal project. Bricolage Grotesque is bundled under the SIL Open Font License (see `art/OFL-BricolageGrotesque.txt`).
