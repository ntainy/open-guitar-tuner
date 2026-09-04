# Architecture

GuitarTuner is a single-activity Compose app with one hand-rolled dependency graph and a pure-JVM DSP module.
Everything user-visible is derived from two flows: `TunerEngine.state` (what the microphone hears) and the
settings/tunings repositories (what the user chose).

```
USB interface ──┐                                                     ┌─► ui/tuner   (gauge, note, headstock, AUTO)
                ├─ AudioSource.samples() ─► FrameAssembler ─► PitchDetector ─► PitchSmoother ─► TargetResolver ─► TunerState
Built-in mic ───┘   (AudioRecord, 48 kHz)    (4096 / 2048)     (YIN)            (median+EMA)    (hysteresis)       │
                    AudioInputMonitor picks the device                                                              ├─► ui/tunings, ui/editor
TuningsRepository  (typed DataStore, tunings.json: custom only; presets from code) ────────────────────────────────┤
SettingsRepository (typed DataStore, settings.json) ───────────────────────────────────────────────────────────────┴─► ui/settings
```

## Modules

| Module | Role | Android? |
|---|---|---|
| `:dsp` | `NoteMath`, YIN detector, frame assembler, smoother, target resolver | No. Plain Kotlin/JVM, tested with synthetic signals. |
| `:app` | Audio capture, persistence, Compose UI, DI | Yes. minSdk 36. |

## Layers inside `:app`

- **audio/** — `AndroidAudioInputMonitor` (device list + hot-plug), `AudioRecordSource` (mic or USB via
  `setPreferredDevice`), `SyntheticToneSource` (debug), `AudioTunerEngine` (the pipeline above, exposes `TunerState`).
- **data/** — serializable models, `PresetTunings` catalog, DataStore-backed `TuningsRepository` and
  `SettingsRepository`. Custom tunings live in `tunings.json`; presets are code so they update with the app.
- **di/AppContainer** — one instance per process. Screens receive it and build their own ViewModel via
  `viewModel(factory = viewModelFactory { initializer { ... } })`. No Hilt.
- **ui/** — one package per screen (`tuner`, `tunings`, `editor`, `settings`), `theme`, and `nav/AppNav`
  (Navigation 3 back stack + bottom bar). Composables are stateless; each ViewModel exposes one `StateFlow<UiState>`.
- **fakes/** — in-memory repositories and a scriptable engine used by previews, ViewModel tests and the M0 skeleton.

## Threads and lifecycle

- Capture runs on a dedicated thread at `THREAD_PRIORITY_URGENT_AUDIO`; chunks flow through a `DROP_OLDEST`
  channel so a slow consumer never stalls `AudioRecord`.
- Analysis (assembler → detector → smoother → resolver) runs on `Dispatchers.Default`, about 23 frames/s.
- The engine runs only while the Tune screen is resumed and `RECORD_AUDIO` is granted; device changes restart
  capture through `flatMapLatest`.

## Input selection

`TunerSettings.inputPolicy` decides: `PREFER_USB` (default) uses the first USB device and falls back to the
built-in mic when unplugged; `BUILTIN_MIC` never leaves the mic; `SPECIFIC_DEVICE` matches on the stable
`AudioInputDevice.key` ("usb:<product name>"). An explicit pick in the input sheet overrides the policy for the
session while that device is present. Debug builds add a synthetic "Test tone" input for UI work without a guitar.

## Persistence

Typed DataStore with kotlinx.serialization: `tunings.json` (`TuningsFile { version, tunings[] }`) and
`settings.json` (`TunerSettings`). Unknown keys are ignored, corrupt files are replaced with defaults, and the
`version` field is the hook for future migrations.

## Testing

- `:dsp:test` — accuracy matrix over synthetic plucked strings (every note B1–G4 at −40…+40 cents within 1 cent),
  silence/noise rejection, smoother and resolver behaviour, per-frame timing.
- `:app:testDebugUnitTest` — engine behaviour with fake sources, repositories against a temp directory,
  ViewModel state derivation. No Robolectric; `android.util.Log` returns defaults.
- On-device — see `docs/PLAN.md` "M5" and `CLAUDE.md` for the adb commands.
