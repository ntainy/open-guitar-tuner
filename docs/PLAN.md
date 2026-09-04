# OpenGuitarTuner MVP — implementation plan (rev 2)

## Context

You use GuitarTuna (lifetime licence) but it caps custom tunings at 3, so adding a fourth means deleting one. You want a personal Android 16 app with the same *string-preview* interaction (target string + cents offset, not a chromatic tuner), unlimited custom tunings, and input from the built-in mic **or** the NUX Mighty Plug Pro MP-3 over USB. Material 3, dark-first, with its own look rather than a GuitarTuna clone. Only you will use it: minSdk 36, no compatibility work.

The repo is an Android Studio 2026.1 "No Activity" scaffold with one commit: AGP 9.4.0, Gradle 9.6, JDK 25 (Studio's JBR via foojay daemon toolchain, no system Java), namespace `dev.ntainy.guitar_tuner`, minSdk 36 / compileSdk 37, AGP built-in Kotlin (2.2.10), Views-era `material`/`appcompat` deps, no Activity. Two untracked headstock illustrations at the root: `guitar-3-3-headstock.avif` (411×770, alpha) and `guitar-6-in-line-headstock.avif` (607×882, alpha).

## Resolved with you

- Guitar, 6 strings only. Two headstock display modes: **3+3** and **6-in-line**, using your artwork.
- Own visual identity, not a GuitarTuna copy. Cents gauge first, scrolling trace later.
- Display name "OpenGuitarTuner" (was "GuitarTuner"), package `dev.ntainy.guitar_tuner` unchanged, bottom navigation bar.
- Repo is already a git repo; commit at each milestone; free to install SDK components.
- Implementation fans out to subagents (waves below).

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose + Material 3 (Compose BOM 2026.08.00) | Compose 1.12 requires compileSdk 37 + AGP 9, already in place |
| Kotlin version | 2.3.21, raised above AGP's built-in 2.2.10 by applying `kotlin-jvm` / `kotlin-compose` / `kotlin-serialization` plugins at 2.3.21 in the root build (AGP docs: a higher KGP on the build classpath wins); fallback is an explicit `buildscript { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21") }` | Compose compiler plugin must equal the Kotlin version; Compose 1.12 docs pair with 2.3.21; Gradle 9.6 embeds 2.3.21 |
| Navigation | Navigation 3 (1.1.7) + `lifecycle-viewmodel-navigation3` | Stable, recommended for new Compose apps, 4 destinations |
| Persistence | Typed DataStore (`tunings.json`, kotlinx.serialization, `version` field) + Preferences DataStore for settings | No KSP/Room/Hilt on a bleeding-edge toolchain; JVM-testable; hundreds of tunings is nothing |
| DI | Manual `AppContainer` in `TunerApplication`, ViewModels via `viewModelFactory` | 4 screens; Hilt buys nothing |
| Audio capture | `android.media.AudioRecord`, 48 kHz mono `ENCODING_PCM_FLOAT`, source `UNPROCESSED` (fallback `VOICE_RECOGNITION`), `setPreferredDevice()` for USB, `AudioDeviceCallback` hot-plug | No NDK; USB Audio Class devices show up as `TYPE_USB_DEVICE`/`TYPE_USB_HEADSET` with no extra permission |
| Pitch detection | Own YIN in a pure-Kotlin `:dsp` JVM module: window 4096, hop 2048 (~23 Hz update), lag 40–1200 samples (40 Hz–1.2 kHz), threshold 0.15, parabolic interpolation, RMS gate, median-of-3 + EMA | ~1.5 M mult-adds/frame; sub-cent on clean signals; absolute-threshold rule resists the 2nd-harmonic octave error |
| Headstock art | Convert the two AVIFs to PNG in `app/src/main/res/drawable-nodpi/` (`headstock_3_3.png`, `headstock_6_in_line.png`); keep originals in `art/`; `HeadstockSpec` holds peg anchor fractions per layout | `painterResource` decodes PNG everywhere; AVIF support in `BitmapFactory` is not guaranteed |
| Visual identity | "Graphite & brass" M3 theme (below), bundled Bricolage Grotesque for display text and the note glyph, Roboto body | Distinct from GuitarTuna's green-on-black; brass = tuning hardware |
| Modules | `:app` (Android) + `:dsp` (pure Kotlin JVM) | Millisecond DSP tests; clean ownership for parallel agents |

## Visual identity ("Graphite & brass", dark-first)

- Dark scheme: background `#0E1117`, surface `#161B24`, surfaceContainer `#1E2431`, surfaceContainerHigh `#262D3B`, outline `#29303D`; primary brass `#E2B65A` (onPrimary `#241A05`, primaryContainer `#3A2E12`); secondary steel `#9AA6BD`; tertiary mint `#3DD68C` = in tune; error coral `#F07A64` = far off; onSurface `#E9ECF2`, onSurfaceVariant `#98A1B3`.
- Light scheme (Settings › Theme: system/dark/light, default dark): background `#F3F4F7`, surface `#FFFFFF`, primary `#9C7118`, tertiary `#1B8F60`, error `#C9503A`, onSurface `#161A23`. No dynamic colour.
- Type: Bricolage Grotesque 500/700 (OFL, two static TTFs in `res/font/`) for headline/display and the 64sp note glyph with a smaller raised octave digit; Roboto (system) for body/label.
- Gauge: horizontal ruler −50…+50 cents, ticks every 10, ♭ left / ♯ right, brass needle that springs to the value, needle turns mint inside tolerance and coral beyond ±25; an offset bubble above the needle ("+3 · Tune down"); soft mint glow behind the note glyph when in tune. Background is a plain surface with a faint radial vignette, no grid.
- String buttons: 56dp circles, outline = outline colour; target = brass ring + brass text; tuned = mint ring + small check; both rings 2dp.

## Target architecture

```
USB interface ──┐                                          ┌─► TunerScreen (gauge, note, headstock, AUTO)
                ├─ AudioRecord (48k mono float) ─► ring buffer ─► YinPitchDetector ─► PitchSmoother ─► TargetResolver ─► TunerState (StateFlow)
Built-in mic ───┘   AudioInputMonitor picks device            (:dsp)                 (:dsp)            (auto/manual)      │
                                                                                                                          ├─► TuningsScreen / TuningEditorScreen
TuningsRepository (typed DataStore, JSON) ◄── presets + custom ────────────────────────────────────────────────────────────┤
SettingsRepository (Preferences DataStore) ───────────────────────────────────────────────────────────────────────────────┴─► SettingsScreen
```

### Package layout

```
app/src/main/java/dev/ntainy/guitar_tuner/
  TunerApplication.kt · MainActivity.kt · di/AppContainer.kt
  audio/   AudioInputMonitor.kt (device list Flow, USB detection) · AudioSource.kt (interface) · AudioRecordSource.kt
           SyntheticToneSource.kt (debug builds) · TunerEngine.kt (capture → dsp → TunerState) · RecordAudioPermission.kt
  data/    model/Tuning.kt · model/TunerSettings.kt · model/HeadstockLayout.kt (all @Serializable)
           presets/PresetTunings.kt · TuningsRepository.kt (DataStore<TuningsFile>) · SettingsRepository.kt
  ui/      theme/ (Color.kt, Type.kt, Theme.kt) · nav/AppNav.kt (Nav3 keys + NavDisplay + NavigationBar)
           tuner/ TunerScreen.kt · TunerViewModel.kt · CentsGauge.kt · Headstock.kt (art + anchors + peg ring) · StringButton.kt · InputPickerSheet.kt
           tunings/ TuningsScreen.kt · TuningsViewModel.kt · TuningRow.kt · NoteChip.kt
           editor/ TuningEditorScreen.kt · TuningEditorViewModel.kt · NotePicker.kt
           settings/ SettingsScreen.kt · SettingsViewModel.kt
  res/drawable-nodpi/headstock_3_3.png · headstock_6_in_line.png · res/font/bricolage_grotesque_{medium,bold}.ttf
dsp/src/main/kotlin/dev/ntainy/guitar_tuner/dsp/
  NoteMath.kt · RingBuffer.kt · PitchDetector.kt · YinPitchDetector.kt · PitchSmoother.kt · TargetResolver.kt
art/  guitar-3-3-headstock.avif · guitar-6-in-line-headstock.avif (originals, moved from root)
docs/ ARCHITECTURE.md · DSP.md · TUNINGS.md
```

### Domain model

- `Note` = MIDI number. `NoteMath.frequency(midi, a4)`, `NoteMath.cents(f, fTarget) = 1200·log2(f/fTarget)`, `NoteMath.name(midi, notation)`.
- `Tuning(id, name, subtitle, strings: List<Int> /* exactly 6 MIDI notes, low→high */, isPreset, group)`. Unlimited custom entries; `STRING_COUNT = 6` constant, validated on save.
- `HeadstockLayout { THREE_PLUS_THREE, SIX_IN_LINE }` + `HeadstockSpec(drawable, pegAnchors: List<Offset /* fractions of image size, index = string 0 (low E) … 5 (high E) */>, buttonSide per string)`.
  - 3+3 (411×770): low E → bottom-left post ≈ (0.28, 0.49), A ≈ (0.28, 0.325), D ≈ (0.28, 0.16); G → top-right ≈ (0.73, 0.16), B ≈ (0.73, 0.325), high E → bottom-right ≈ (0.73, 0.49). Buttons: left column D/A/E(low) top→bottom, right column G/B/E(high).
  - 6-in-line (607×882): posts run diagonally from high E (top) ≈ (0.53, 0.19) to low E (bottom) ≈ (0.34, 0.585), one per ≈0.079 of height. Buttons: a single left column aligned to each peg row, high E on top.
  - Agent D refines the anchors visually in a Compose preview (overlay the ring on each post).
- `TunerSettings`: `a4Hz` (440, range 415–466), `toleranceCents` (3), `autoMode` (true), `inputPolicy` (PREFER_USB / BUILTIN_MIC / DEVICE(id)), `notation` (SHARPS/FLATS), `headstockLayout`, `keepScreenOn` (true), `theme` (SYSTEM/DARK/LIGHT, default DARK), `showHz`, `activeTuningId`.
- `TunerState`: `pitchHz?`, `confidence`, `level`, `targetIndex`, `centsOff?`, `inTune`, `tunedStrings: Set<Int>`, `input: AudioInputDevice?`, `permissionGranted`.
- Presets (all 6-string), grouped: **Standard** (E A D G B E); **Power** Drop D, Double Drop D, D modal (DADGAD), Double Daddy (DADDAD), Drop C♯, Drop C, Drop B; **Transposed** −1 (E♭), −2 (D), +1 (F), +2 (F♯), C standard, B standard; **Open** Open G, Open D, Open Dm, Open E, Open A, Open C; **Extras** G modal, All 4th, NST (CGDAEG).

### Behaviour

- AUTO on: `TargetResolver` picks the string nearest (in cents) to the detected pitch; switches only when another string is closer by >30 cents for 3 consecutive readings. Tapping a string pins it and turns AUTO off; the switch turns it back on.
- Cents are relative to the target string; |cents| ≤ tolerance for ~8 consecutive readings marks the string tuned. "Start over" clears marks.
- Input policy PREFER_USB: use USB input when present, fall back to the mic on unplug; capture restarts on device change. Input sheet lists devices with type icon + product name; debug builds add "Test tone" (synthetic source, adjustable Hz).
- Engine runs only while the Tune screen is resumed and permission is granted; capture thread at `THREAD_PRIORITY_URGENT_AUDIO`, analysis on `Dispatchers.Default`.

## Build plan

### M0 — Foundation (me, sequential) → commit "M0 foundation"
- `gradle/libs.versions.toml` rewrite; root `build.gradle.kts` (plugins at 2.3.21, `apply false`); `settings.gradle.kts` add `:dsp`; `app/build.gradle.kts` (Compose, serialization, `compileOptions` 17, drop `appcompat`/`material`); `dsp/build.gradle.kts` (`kotlin("jvm")`, junit); `gradle.properties` (`org.gradle.parallel=true`).
- Manifest: `RECORD_AUDIO`, `uses-feature microphone required=false`, `MainActivity` exported; platform theme `android:Theme.Material.NoActionBar`, `enableEdgeToEdge()`; delete the Views `themes.xml`/`colors.xml`.
- Theme (colours/type above), Nav3 skeleton with `NavigationBar` and four placeholder screens, `AppContainer`, **all interfaces and models above as frozen contracts** with KDoc, `SyntheticToneSource` stub.
- Art: `sips -s format png` the AVIFs into `drawable-nodpi`, move originals to `art/`. Fonts: download the two Bricolage Grotesque static TTFs (OFL) into `res/font/`.
- `CLAUDE.md` (build command with `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, module map, contracts, conventions), `README.md` skeleton, `.editorconfig`.
- Exit: `./gradlew assembleDebug :dsp:test :app:testDebugUnitTest` green; `./gradlew buildEnvironment` shows KGP 2.3.21.

### Wave 1 — three subagents in parallel → commit "Wave 1: dsp, audio, data"
- **A · `:dsp`**: `NoteMath`, `RingBuffer`, `YinPitchDetector`, `PitchSmoother`, `TargetResolver`. Tests: synthetic plucked string (8 harmonics, 2nd at 1.3× fundamental, inharmonicity B≈2e-4, −30 dB noise) for every preset string at −40/−10/0/+10/+40 cents → |error| < 1 cent; silence/noise → null; resolver hysteresis; one 4096 frame < 2 ms.
- **B · audio**: `AudioInputMonitor`, `AudioRecordSource`, `SyntheticToneSource`, `TunerEngine`, permission helper; logs device list + chosen input. Tests: engine driven by a fake `AudioSource`.
- **C · data**: serializable models, presets catalog (list above, MIDI numbers), `TuningsRepository` (create/edit/delete/duplicate-from-preset/ordering), `SettingsRepository`, JSON version guard. JVM tests with temp-dir DataStore + Turbine; presets have unique ids and 6 notes each.

### Wave 2 — two subagents in parallel → commit "Wave 2: UI"
- **D · Tune screen**: `TunerScreen` (tuning breadcrumb, AUTO switch, input button), `CentsGauge`, note glyph, `Headstock` (artwork + anchors + brass ring on the active post, both layouts), `StringButton` states, `InputPickerSheet`, "Start over", keep-screen-on. Compose previews: idle, flat, sharp, in tune, all tuned, USB selected, permission denied, both layouts.
- **E · Tunings / Editor / Settings**: grouped list (My tunings first), selection check, headstock-layout segmented button, FAB "New tuning", swipe/overflow edit-duplicate-delete, long-press preset → copy to mine; editor with name, 6 rows of −/+ semitone + note picker, "shift all ±1", live chip preview, validation; settings for every field.

### M5 — Integration + device verification (me, with you) → commit "M5 integration"
- Wire waves, run tests, install on your phone, screenshot every screen/state via `adb exec-out screencap -p`.
- Accuracy: debug test tone 110.0 Hz → A2 at 0 ± 1 cent; ±10 cents follows; 82.41 Hz → E2.
- USB: plug the MP-3 in, confirm it is listed and auto-selected, pluck each string in Standard and Drop D, AUTO picks the right string, tuned rings appear; unplug → mic. `adb logcat -s TunerEngine AudioInputMonitor`.
- Tune YIN threshold / hop / smoothing against real strings. Finish README + docs.

### M6 — Polish (optional) 
Scrolling pitch trace, haptic tick when in tune, app icon (brass peg on graphite), release build with R8.

## Verification
1. `JAVA_HOME=… ./gradlew :dsp:test :app:testDebugUnitTest assembleDebug` green after each wave.
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n dev.ntainy.guitar_tuner/.MainActivity`; screenshots reviewed per state.
3. Test-tone checks (above) and real guitar via mic and MP-3.
4. Create > 3 custom tunings, kill and relaunch: all persist, last selected active.
5. Both headstock layouts: the brass ring sits on the correct post for every string.

## What I need from you
- Nothing until M5. Then: phone with USB debugging, the MP-3 and a guitar. I'll ping you on Telegram when it's time.
