package dev.ntainy.guitar_tuner.ui.tuner

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.audio.TunerState
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.presets.PresetTunings
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.fakes.FakeTunerEngine
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme

val PREVIEW_BUILTIN_MIC = AudioInputDevice(
    id = 0,
    key = AudioInputDevice.BUILTIN_KEY,
    name = "Built-in microphone",
    kind = InputKind.BUILTIN_MIC,
)
val PREVIEW_USB = AudioInputDevice(
    id = 12,
    key = "usb:NUX MP-3",
    name = "NUX Mighty Plug Pro (USB)",
    kind = InputKind.USB,
)
val PREVIEW_TEST_TONE = AudioInputDevice(
    id = -1,
    key = AudioInputDevice.TEST_TONE_KEY,
    name = "Test tone",
    kind = InputKind.TEST_TONE,
)

/** [AppContainer] wired to the fakes, remembering what it was built from so previews can derive the UI state. */
class PreviewContainer(
    context: Context,
    val engineState: TunerState,
    val settings: TunerSettings,
    val tuning: Tuning,
) : AppContainer(context) {
    override val tunerEngine: TunerEngine by lazy { FakeTunerEngine(engineState) }

    override val settingsRepository: SettingsRepository by lazy { InMemorySettingsRepository(settings) }

    override val tuningsRepository: TuningsRepository by lazy {
        InMemoryTuningsRepository(
            listOf(InMemoryTuningsRepository.STANDARD, InMemoryTuningsRepository.DROP_D, tuning).distinctBy { it.id },
        )
    }

    /** The state the ViewModel would produce from this container, through the same derivation. */
    fun uiState(selectedInputKey: String? = null): TunerUiState =
        deriveTunerUiState(tunerEngine.state.value, settings, tuning, testToneFrequencyHz.value, selectedInputKey)
}

@Composable
fun previewContainer(
    state: TunerState = TunerState(
        permissionGranted = true,
        running = true,
        input = PREVIEW_BUILTIN_MIC,
        availableInputs = listOf(PREVIEW_BUILTIN_MIC, PREVIEW_TEST_TONE),
    ),
    settings: TunerSettings = TunerSettings(),
    tuning: Tuning = InMemoryTuningsRepository.STANDARD,
): PreviewContainer {
    val context = LocalContext.current
    return remember(state, settings, tuning) { PreviewContainer(context, state, settings, tuning) }
}

private val LISTENING = TunerState(
    permissionGranted = true,
    running = true,
    input = PREVIEW_BUILTIN_MIC,
    availableInputs = listOf(PREVIEW_BUILTIN_MIC, PREVIEW_TEST_TONE),
)

@Composable
private fun PreviewScreen(
    state: TunerUiState,
    dark: Boolean = true,
    permanentlyDenied: Boolean = false,
    trace: List<TracePoint> = emptyList(),
) {
    GuitarTunerTheme(darkTheme = dark) {
        TunerContent(
            state = state,
            permanentlyDenied = permanentlyDenied,
            trace = trace,
            onOpenTunings = {},
            onSelectString = {},
            onAutoMode = {},
            onStartOver = {},
            onOpenInput = {},
            onAllowMic = {},
            onOpenAppSettings = {},
        )
    }
}

private const val PHONE_WIDTH = 411
private const val PHONE_HEIGHT = 780
private const val LANDSCAPE_WIDTH = 892
private const val LANDSCAPE_HEIGHT = 411

/**
 * A plausible six seconds: a pluck that arrives sharp, is turned down past the target, and settles inside the
 * band, with a silent stretch before the next attack. Enough shape to judge the drawing by.
 */
private fun previewTrace(): List<TracePoint> {
    val step = 40L
    val count = (TRACE_WINDOW_MS / step).toInt()
    return List(count) { i ->
        val t = i * step
        val cents = when {
            i < count / 8 -> null
            else -> {
                val progress = (i - count / 8).toFloat() / (count - count / 8)
                val settle = 34f * kotlin.math.exp(-3.2f * progress)
                val wobble = kotlin.math.sin(progress * 26f).toFloat() * 3.5f * (1f - progress)
                (settle * kotlin.math.cos(progress * 5.5f).toFloat() + wobble)
            }
        }
        TracePoint(atMs = t, cents = cents)
    }
}

@Preview(name = "Idle, no signal", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun IdlePreview() {
    PreviewScreen(previewContainer(LISTENING).uiState())
}

@Preview(name = "Flat −18", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun FlatPreview() {
    val state = LISTENING.copy(pitchHz = 81.56, confidence = 0.9, level = 0.4, targetIndex = 0, centsOff = -18.0)
    PreviewScreen(previewContainer(state).uiState())
}

@Preview(name = "Sharp +3", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun SharpPreview() {
    val state = LISTENING.copy(pitchHz = 110.22, confidence = 0.9, level = 0.4, targetIndex = 1, centsOff = 3.4)
    PreviewScreen(previewContainer(state).uiState())
}

@Preview(name = "In tune", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun InTunePreview() {
    val state = LISTENING.copy(
        pitchHz = 146.86,
        confidence = 0.95,
        level = 0.5,
        targetIndex = 2,
        centsOff = 0.4,
        inTune = true,
        tunedStrings = setOf(0, 1),
    )
    PreviewScreen(previewContainer(state).uiState())
}

@Preview(name = "All strings tuned", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun AllTunedPreview() {
    val state = LISTENING.copy(
        pitchHz = 329.7,
        confidence = 0.95,
        level = 0.5,
        targetIndex = 5,
        centsOff = 0.2,
        inTune = true,
        tunedStrings = setOf(0, 1, 2, 3, 4, 5),
    )
    PreviewScreen(previewContainer(state).uiState())
}

@Preview(name = "USB input, manual, Drop D", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun UsbPreview() {
    val state = LISTENING.copy(
        input = PREVIEW_USB,
        availableInputs = listOf(PREVIEW_BUILTIN_MIC, PREVIEW_USB, PREVIEW_TEST_TONE),
        autoMode = false,
        targetIndex = 0,
        pitchHz = 71.1,
        centsOff = -38.0,
    )
    val container = previewContainer(
        state = state,
        settings = TunerSettings(activeTuningId = InMemoryTuningsRepository.DROP_D.id, notation = Notation.FLATS),
        tuning = InMemoryTuningsRepository.DROP_D,
    )
    PreviewScreen(container.uiState(selectedInputKey = PREVIEW_USB.key))
}

@Preview(name = "Permission denied", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun PermissionDeniedPreview() {
    PreviewScreen(previewContainer(TunerState(permissionGranted = false)).uiState())
}

@Preview(name = "Permission denied permanently", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun PermissionDeniedPermanentlyPreview() {
    PreviewScreen(previewContainer(TunerState(permissionGranted = false)).uiState(), permanentlyDenied = true)
}

@Preview(name = "6-in-line headstock", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun SixInLinePreview() {
    val state = LISTENING.copy(pitchHz = 195.4, targetIndex = 3, centsOff = -5.0, tunedStrings = setOf(4, 5))
    val container = previewContainer(state, settings = TunerSettings(headstockLayout = HeadstockLayout.SIX_IN_LINE))
    PreviewScreen(container.uiState())
}

@Preview(name = "Light theme, in tune", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun LightPreview() {
    val state = LISTENING.copy(
        pitchHz = 246.9,
        targetIndex = 4,
        centsOff = 1.0,
        inTune = true,
        tunedStrings = setOf(0, 1, 2, 3),
    )
    PreviewScreen(previewContainer(state, settings = TunerSettings(showHz = false)).uiState(), dark = false)
}

@Preview(name = "Input sheet, USB + test tone", showBackground = true, widthDp = PHONE_WIDTH)
@Composable
private fun InputSheetPreview() {
    val state = LISTENING.copy(
        input = PREVIEW_TEST_TONE,
        availableInputs = listOf(PREVIEW_BUILTIN_MIC, PREVIEW_USB, PREVIEW_TEST_TONE),
        error = "USB device disconnected, using the microphone",
    )
    GuitarTunerTheme {
        Surface {
            InputPickerContent(
                state = previewContainer(state).uiState(selectedInputKey = PREVIEW_TEST_TONE.key),
                onSelectInput = {},
                onTestToneHz = {},
            )
        }
    }
}

@Preview(name = "Gauge states", showBackground = true, widthDp = PHONE_WIDTH)
@Composable
private fun GaugePreview() {
    GuitarTunerTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CentsGauge(centsOff = null, inTune = false, toleranceCents = 3.0, hint = TunerUiState.HINT_PLAY)
                CentsGauge(centsOff = -18.0, inTune = false, toleranceCents = 3.0, hint = TunerUiState.HINT_FLAT)
                CentsGauge(centsOff = 1.2, inTune = true, toleranceCents = 3.0, hint = TunerUiState.HINT_IN_TUNE)
                CentsGauge(centsOff = 41.0, inTune = false, toleranceCents = 3.0, hint = TunerUiState.HINT_SHARP)
            }
        }
    }
}

@Preview(name = "String buttons", showBackground = true)
@Composable
private fun StringButtonPreview() {
    GuitarTunerTheme {
        Surface {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StringButton(label = "E2", state = StringButtonState.IDLE, onClick = {})
                StringButton(label = "A2", state = StringButtonState.TARGET, onClick = {})
                StringButton(label = "D3", state = StringButtonState.TUNED, onClick = {})
                StringButton(label = "G♯3", state = StringButtonState.TARGET_TUNED, onClick = {})
            }
        }
    }
}

@Preview(name = "Note glyphs", showBackground = true)
@Composable
private fun NoteGlyphPreview() {
    GuitarTunerTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val scheme = MaterialTheme.colorScheme
                NoteGlyph(
                    letter = null,
                    octave = null,
                    ringColor = needleColor(null, false, scheme),
                    inTune = false,
                    pitchLabel = null,
                )
                NoteGlyph(
                    letter = "E",
                    octave = "2",
                    ringColor = needleColor(-18.0, false, scheme),
                    inTune = false,
                    pitchLabel = "81.56 Hz",
                )
                NoteGlyph(
                    letter = "F♯",
                    octave = "3",
                    ringColor = needleColor(0.4, true, scheme),
                    inTune = true,
                    pitchLabel = "185.04 Hz",
                )
            }
        }
    }
}

@Preview(name = "Pitch trace on", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun TracePreview() {
    val state = LISTENING.copy(pitchHz = 110.4, confidence = 0.9, level = 0.4, targetIndex = 1, centsOff = 2.1, inTune = true)
    PreviewScreen(previewContainer(state).uiState(), trace = previewTrace())
}

@Preview(name = "Landscape", showBackground = true, widthDp = LANDSCAPE_WIDTH, heightDp = LANDSCAPE_HEIGHT)
@Composable
private fun LandscapePreview() {
    val state = LISTENING.copy(pitchHz = 146.9, confidence = 0.9, level = 0.4, targetIndex = 2, centsOff = -7.0)
    PreviewScreen(previewContainer(state).uiState(), trace = previewTrace())
}

@Preview(name = "Landscape, 6-in-line, light", showBackground = true, widthDp = LANDSCAPE_WIDTH, heightDp = LANDSCAPE_HEIGHT)
@Composable
private fun LandscapeLightPreview() {
    val state = LISTENING.copy(pitchHz = 329.9, confidence = 0.9, level = 0.4, targetIndex = 5, centsOff = 1.0, inTune = true)
    val container = previewContainer(state, settings = TunerSettings(headstockLayout = HeadstockLayout.SIX_IN_LINE))
    PreviewScreen(container.uiState(), dark = false, trace = previewTrace())
}

@Preview(name = "Chromatic, C4", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun ChromaticPreview() {
    val state = LISTENING.copy(
        pitchHz = 261.9,
        confidence = 0.9,
        level = 0.4,
        chromaticMidi = 60,
        centsOff = 1.8,
        inTune = true,
    )
    PreviewScreen(previewContainer(state, tuning = CHROMATIC_TUNING).uiState())
}

@Preview(name = "Chromatic, listening", showBackground = true, widthDp = PHONE_WIDTH, heightDp = PHONE_HEIGHT)
@Composable
private fun ChromaticIdlePreview() {
    PreviewScreen(previewContainer(LISTENING, tuning = CHROMATIC_TUNING).uiState())
}

private val CHROMATIC_TUNING: Tuning = checkNotNull(PresetTunings.byId(PresetIds.CHROMATIC))
