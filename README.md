# GuitarTuner

A personal Android guitar tuner with the GuitarTuna interaction and none of its limits: you see your six strings on
a headstock, the app tells you which string it hears and how many cents you are off, and you can keep as many
custom tunings as you like. Listens through the phone microphone or through a USB audio interface such as the
NUX Mighty Plug Pro MP-3.

Built for one phone running Android 16, so there is no compatibility baggage: minSdk 36, Kotlin, Jetpack Compose,
Material 3, and a small hand-written YIN pitch detector.

## Status

Milestone 0 (foundation) is in place: build, theme, navigation skeleton, contracts. See `docs/PLAN.md` for the
roadmap and `CLAUDE.md` for build commands and conventions.

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
