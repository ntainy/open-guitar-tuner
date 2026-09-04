# OpenGuitarTuner — working notes for Claude Code

Open source Android 16 guitar tuner (6-string only): GuitarTuna-style string preview with cents offset, unlimited
custom tunings, mic or USB audio input (NUX Mighty Plug Pro MP-3). Kotlin + Jetpack Compose + Material 3.
The plan that drives the work lives at `docs/PLAN.md`. GPL-3.0, at <https://github.com/ntainy/open-guitar-tuner>.

The product name is **OpenGuitarTuner**, but the `applicationId`, `namespace` and Kotlin package stay
`dev.ntainy.guitar_tuner` so the app updates in place and keeps the user's saved tunings. Do not rename them, and do
not rename Kotlin identifiers such as `GuitarTunerTheme`.

## Build

There is no system Java. Every Gradle invocation from a shell needs Android Studio's bundled JDK:

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :dsp:test :app:testDebugUnitTest assembleDebug      # the green bar for every milestone
./gradlew buildEnvironment | grep kotlin-gradle-plugin         # must resolve to 2.3.21
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n dev.ntainy.guitar_tuner/.MainActivity
adb exec-out screencap -p > /tmp/shot.png                      # then Read the PNG
adb logcat -s TunerEngine AudioInputMonitor                    # device choice, capture start/stop
adb logcat -v time -s TunerFrames:V > frames.txt               # debug builds: one line per analysis frame
./gradlew assembleRelease                                      # R8 on; unsigned unless a keystore is configured
```

Release builds run R8 (`optimization { enable = true }`); keep rules live in `app/src/main/keepRules/`, not in a
`proguard-rules.pro` (AGP 9 moved them). Signing reads `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` from the environment, then `local.properties`; with none set the build
still succeeds and emits an unsigned APK. Never commit a credential. CI (`.github/workflows/ci.yml`) runs
`:dsp:test :app:testDebugUnitTest assembleDebug` on every push and PR; a `v*` tag triggers
`.github/workflows/release.yml`. `docs/RELEASING.md` has the keystore and secret setup.

Toolchain: AGP 9.4.0 (built-in Kotlin, raised to 2.3.21 via the root plugins block), Gradle 9.6, JDK 25 daemon,
compileSdk 37, minSdk 36. Compose BOM 2026.08.00, Navigation 3, typed DataStore + kotlinx.serialization.
No Hilt, no Room, no KSP, no NDK. Do not add them.

## Modules and packages

- `:dsp` — pure Kotlin/JVM, no Android imports. `NoteMath` (midi/Hz/cents/names), `PitchDetector` (+`PitchEstimate`,
  `DspDefaults`), `FrameAssembler`, `PitchSmoother`, `TargetResolver`. Tests in `dsp/src/test`.
- `:app` — `dev.ntainy.guitar_tuner`
  - `audio/` — `AudioInputDevice`, `AudioSource`, `AudioInputMonitor`, `TunerEngine` + `TunerState`.
  - `data/` — `model/` (`Tuning`, `TunerSettings`, enums), `TuningsRepository`, `SettingsRepository`, `presets/`.
  - `fakes/` — in-memory repositories and `FakeTunerEngine` for previews, tests and the skeleton.
  - `di/AppContainer` — the hand-rolled graph. Screens take the container and build their own ViewModel with
    `viewModel(factory = viewModelFactory { initializer { ... } })`.
  - `ui/theme` — "Graphite & brass" colour schemes, `Bricolage` font family, `ColorScheme.inTune/farOff/target`.
  - `ui/nav/AppNav` — Navigation 3 back stack, bottom bar (Tune / Tunings / Settings), `EditorKey`.
  - `ui/tuner`, `ui/tunings`, `ui/editor`, `ui/settings` — one package per screen.

## Contracts (frozen after M0)

The interfaces above are the seams between parallel work. Implement them; do not change their signatures
without updating every caller and this file. Tunings always have exactly `STRING_COUNT = 6` MIDI notes, low
string first (index 0 = low E in Standard). `TunerState.centsOff` is signed, positive = sharp. The one exception to "always six strings" is the chromatic
entry (`Tuning.isChromatic`, id `PresetIds.CHROMATIC`): it carries Standard's six MIDI notes so it validates like any
other tuning, and they are never read.

Feel: `ui/haptics/TunerHaptics.kt` is the whole haptic vocabulary, provided once in `MainActivity` from the
`haptics` setting — call it by meaning (`step`, `toggle`, `confirm`, `enterBand`, `allTuned`), never
`performHapticFeedback` (which the system-wide touch-feedback setting silently suppresses) and never the vibrator
directly. Motion comes from `MaterialTheme.motionScheme` under `MaterialExpressiveTheme`; use
`MaterialTheme.motionScheme.*Spec()` rather than a hand-written `spring()`. material3 is pinned to a 1.5.0 alpha
ahead of the Compose BOM for that API — see `libs.versions.toml`.

Audio chain: `AudioSource.samples()` (48 kHz mono float chunks) → `FrameAssembler` (4096 window, 2048 hop) →
`PitchDetector.detect` (YIN) → `NoiseGate` → `OctaveGuard` → `PitchSmoother.push` → `TargetResolver.resolveMatch`
(overtone folds) → `TunerState`. When the active tuning `isChromatic` the chain stops after the octave guard: no
resolver, no folds, target = `NoteMath.nearestMidi`, published as `TunerState.chromaticMidi` with `targetIndex` null. `OnsetDetector` holds the reading ~300 ms after each attack. `docs/DSP.md` explains
every stage and what was measured on the phone; change parameters there and in `AppContainer`, not in call sites.

## Conventions

- Kotlin official style, 4-space indent, 120 columns, trailing commas in multi-line calls.
- Compose: stateless composables + a `@Preview` per visual state; ViewModels expose one `StateFlow<UiState>`.
- Colours only through `MaterialTheme.colorScheme` (and the semantic aliases); never hard-code hex in screens.
- Semantic naming: mint/tertiary = in tune, brass/primary = target, coral/error = far off.
- Tests: JUnit4 + kotlin-test; coroutines via `runTest`; flows via Turbine. No Robolectric.
- Logging: `Log.d("TunerEngine", ...)` / `Log.d("AudioInputMonitor", ...)` tags are what the verification steps grep.
- Commit per milestone with a plain message (`M0 foundation`, `Wave 1: dsp, audio, data`, ...). No attribution lines.

## Design

Dark-first Material 3. Palette and component rules are in `docs/PLAN.md` ("Visual identity"). Headstock art is
`res/drawable-nodpi/headstock_3_3.png` (411×770) and `headstock_6_in_line.png` (607×882); originals in `art/`.
