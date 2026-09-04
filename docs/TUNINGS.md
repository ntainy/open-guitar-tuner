# Tunings

## Presets

The catalog lives in `app/src/main/java/dev/ntainy/guitar_tuner/data/presets/PresetTunings.kt` and is shown in
this order on the Tunings screen, after the user's own tunings. Strings are MIDI numbers, low string first
(index 0 is the thickest string; MIDI 69 = A4 = 440 Hz). Note names below use sharps; Settings › Notation switches
the UI to flats. Every id is `preset_` + slug, and `PresetIds.STANDARD` (`preset_standard`) is the default active
tuning.

| Group | Name | Id | Notes (low → high) | MIDI |
|---|---|---|---|---|
| Standard | Standard | `preset_standard` | E2 A2 D3 G3 B3 E4 | 40 45 50 55 59 64 |
| Power | Drop D | `preset_drop_d` | D2 A2 D3 G3 B3 E4 | 38 45 50 55 59 64 |
| Power | Double Drop D | `preset_double_drop_d` | D2 A2 D3 G3 B3 D4 | 38 45 50 55 59 62 |
| Power | D modal (DADGAD) | `preset_dadgad` | D2 A2 D3 G3 A3 D4 | 38 45 50 55 57 62 |
| Power | Double Daddy (DADDAD) | `preset_daddad` | D2 A2 D3 D3 A3 D4 | 38 45 50 50 57 62 |
| Power | Drop C♯ | `preset_drop_c_sharp` | C♯2 G♯2 C♯3 F♯3 A♯3 D♯4 | 37 44 49 54 58 63 |
| Power | Drop C | `preset_drop_c` | C2 G2 C3 F3 A3 D4 | 36 43 48 53 57 62 |
| Power | Drop B | `preset_drop_b` | B1 F♯2 B2 E3 G♯3 C♯4 | 35 42 47 52 56 61 |
| Transposed | Half step down (E♭ standard) | `preset_half_step_down` | D♯2 G♯2 C♯3 F♯3 A♯3 D♯4 | 39 44 49 54 58 63 |
| Transposed | Whole step down (D standard) | `preset_whole_step_down` | D2 G2 C3 F3 A3 D4 | 38 43 48 53 57 62 |
| Transposed | Half step up (F standard) | `preset_half_step_up` | F2 A♯2 D♯3 G♯3 C4 F4 | 41 46 51 56 60 65 |
| Transposed | Whole step up (F♯ standard) | `preset_whole_step_up` | F♯2 B2 E3 A3 C♯4 F♯4 | 42 47 52 57 61 66 |
| Transposed | C standard | `preset_c_standard` | C2 F2 A♯2 D♯3 G3 C4 | 36 41 46 51 55 60 |
| Transposed | B standard | `preset_b_standard` | B1 E2 A2 D3 F♯3 B3 | 35 40 45 50 54 59 |
| Open | Open G | `preset_open_g` | D2 G2 D3 G3 B3 D4 | 38 43 50 55 59 62 |
| Open | Open D | `preset_open_d` | D2 A2 D3 F♯3 A3 D4 | 38 45 50 54 57 62 |
| Open | Open Dm | `preset_open_dm` | D2 A2 D3 F3 A3 D4 | 38 45 50 53 57 62 |
| Open | Open E | `preset_open_e` | E2 B2 E3 G♯3 B3 E4 | 40 47 52 56 59 64 |
| Open | Open A | `preset_open_a` | E2 A2 E3 A3 C♯4 E4 | 40 45 52 57 61 64 |
| Open | Open C | `preset_open_c` | C2 G2 C3 G3 C4 E4 | 36 43 48 55 60 64 |
| Extras | G modal (DGDGCD) | `preset_g_modal` | D2 G2 D3 G3 C4 D4 | 38 43 50 55 60 62 |
| Extras | All fourths (EADGCF) | `preset_all_fourths` | E2 A2 D3 G3 C4 F4 | 40 45 50 55 60 65 |
| Extras | NST (CGDAEG) | `preset_nst` | C2 G2 D3 A3 E4 G4 | 36 43 50 57 64 67 |

Presets are never written to disk. `DataStoreTuningsRepository` appends the catalog after the user's tunings on
every read, so a change to `PresetTunings.kt` reaches the device with the next install without any migration.
Editing a preset in the app means duplicating it first (long-press, or Duplicate in the row menu); the copy lands in
"My tunings".

## Custom tunings on disk

Custom tunings are a typed DataStore document, `tunings.json`, serialised with kotlinx.serialization. The shape is
`TuningsFile` (`app/src/main/java/dev/ntainy/guitar_tuner/data/TuningsFile.kt`):

```json
{
    "version": 1,
    "tunings": [
        {
            "id": "custom_6f0a2f4e-1b0e-4c8b-9a5d-3c2e1f7d8a90",
            "name": "Drop D, half down",
            "subtitle": "",
            "strings": [37, 44, 49, 54, 58, 63],
            "isPreset": false,
            "group": "MINE",
            "createdAt": 1788000000000
        }
    ]
}
```

- `version` is stamped on every write (`TuningsFile.CURRENT_VERSION`, currently 1). Bump it when the shape of
  `Tuning` or the file changes, and branch on it in the reader to migrate old documents.
- Only custom tunings are stored: every entry has `isPreset = false` and `group = "MINE"`; the repository forces
  both on save and rejects anything whose id starts with `preset_`.
- `strings` is always six MIDI numbers in `24..84` (C1..C6), low string first. `Tuning.validationErrors()` is the
  single source of truth for what can be saved.
- `createdAt` is epoch milliseconds; the list is ordered by it ascending (oldest first), ties broken by id. Editing
  keeps the original timestamp so a tuning does not jump around the list.
- `id` is `custom_` followed by a random UUID.
- Unknown keys are ignored when reading, so a file written by a newer build still loads. A file that cannot be
  parsed at all is replaced by an empty document and the presets carry on working.

On the device the file lives in the app's private DataStore directory, `context.dataStoreFile("tunings.json")`:

```
/data/data/dev.ntainy.guitar_tuner/files/datastore/tunings.json
/data/data/dev.ntainy.guitar_tuner/files/datastore/settings.json
```

Read it from a debug build with `adb shell run-as dev.ntainy.guitar_tuner cat files/datastore/tunings.json`.
`res/xml/backup_rules.xml` and `data_extraction_rules.xml` are still the Android Studio stubs with no rules, so both
files are included in Android auto backup and device-to-device transfer by default.

## Settings on disk

`settings.json` holds one `TunerSettings` document with the same serializer and the same corruption fallback
(defaults). `a4Hz` is clamped to `415..466` and `toleranceCents` to `1..10` on every write and again on read, so a
hand-edited file can never push the tuner out of range. `activeTuningId` may point at a preset or a custom id; the
app falls back to Standard when the id no longer exists.
