package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.runtime.Immutable
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.TunerState
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.STRING_COUNT
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.dsp.NoteMath
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One string of the active tuning, ready to draw. [index] 0 is the lowest string (string 6 on a guitar). */
@Immutable
data class StringUi(
    val index: Int,
    val label: String,
    val isTarget: Boolean,
    val isTuned: Boolean,
    val frequencyHz: Double,
) {
    /** Guitar numbering: the lowest string is string 6, the highest string 1. */
    val number: Int get() = STRING_COUNT - index

    /** What TalkBack reads for the string button, e.g. "String 6, E2, target". */
    val accessibilityLabel: String
        get() = buildString {
            append("String ").append(number).append(", ").append(label)
            if (isTarget) append(", target")
            if (isTuned) append(", tuned")
        }
}

/**
 * Everything the Tune screen draws, derived from [TunerState], [TunerSettings], the active [Tuning]
 * and the test-tone frequency by [deriveTunerUiState]. Display-ready: labels are already formatted.
 */
@Immutable
data class TunerUiState(
    val tuningName: String = "",
    val strings: List<StringUi> = emptyList(),
    /** Full target note name, e.g. "E2"; null while no string is targeted. */
    val targetLabel: String? = null,
    /** Target letter with accidental ("E", "F♯") and its octave ("2"), for the note glyph. */
    val targetLetter: String? = null,
    val targetOctave: String? = null,
    val centsOff: Double? = null,
    /** Signed integer cents, e.g. "+3", "−12", "0"; null without a pitch. */
    val centsLabel: String? = null,
    val inTune: Boolean = false,
    val hint: String = HINT_PLAY,
    val pitchHz: Double? = null,
    val showHz: Boolean = true,
    val autoMode: Boolean = true,
    val input: AudioInputDevice? = null,
    /** Short label for the header, e.g. "Mic", "USB", "Test tone". */
    val inputCaption: String = CAPTION_NONE,
    val availableInputs: List<AudioInputDevice> = emptyList(),
    /** Key of the input the user picked explicitly in the sheet; null means "Auto". */
    val selectedInputKey: String? = null,
    val permissionGranted: Boolean = false,
    val running: Boolean = false,
    val error: String? = null,
    /** Chromatic mode: no strings, no AUTO, no tuned marks — just the nearest note to whatever is playing. */
    val chromatic: Boolean = false,
    /** In chromatic mode, the MIDI note the reading is measured against; null otherwise. */
    val chromaticMidi: Int? = null,
    /** Sharps or flats, for the note strip; every other label is already spelled. */
    val notation: Notation = Notation.SHARPS,
    val headstockLayout: HeadstockLayout = HeadstockLayout.THREE_PLUS_THREE,
    val keepScreenOn: Boolean = true,
    val toleranceCents: Double = 3.0,
    val testToneHz: Double = 110.0,
    /** Nearest note to [testToneHz], e.g. "A2". */
    val testToneNote: String = "A2",
) {
    val anyTuned: Boolean get() = strings.any { it.isTuned }

    val allTuned: Boolean get() = strings.isNotEmpty() && strings.all { it.isTuned }

    val isTestToneInput: Boolean get() = input?.kind == InputKind.TEST_TONE

    companion object {
        const val HINT_PLAY = "Play a string"
        const val HINT_IN_TUNE = "In tune"
        const val HINT_FLAT = "Tune up"
        const val HINT_SHARP = "Tune down"
        const val CAPTION_NONE = "No input"
        const val TEST_TONE_MIN_HZ = 60.0
        const val TEST_TONE_MAX_HZ = 400.0
    }
}

/** Pure mapping from the engine + data layer to the screen model; unit-tested without Compose or Android. */
fun deriveTunerUiState(
    engine: TunerState,
    settings: TunerSettings,
    tuning: Tuning,
    testToneHz: Double,
    selectedInputKey: String?,
): TunerUiState {
    val notation = settings.notation
    val chromatic = tuning.isChromatic
    val strings = if (chromatic) emptyList() else tuning.strings.mapIndexed { index, midi ->
        StringUi(
            index = index,
            label = NoteMath.name(midi, notation),
            isTarget = engine.targetIndex == index,
            isTuned = index in engine.tunedStrings,
            frequencyHz = NoteMath.frequency(midi, settings.a4Hz),
        )
    }
    // In chromatic mode the note comes from the engine's nearest-note reading, not from a string of the tuning.
    val targetMidi = if (chromatic) engine.chromaticMidi else engine.targetIndex?.let { tuning.strings.getOrNull(it) }
    val cents = engine.centsOff
    // The engine owns the flag, but the gauge draws a ±tolerance band, so anything inside it must read as in tune.
    val inTune = cents != null && (engine.inTune || abs(cents) <= settings.toleranceCents)
    val hint = when {
        cents == null -> TunerUiState.HINT_PLAY
        inTune -> TunerUiState.HINT_IN_TUNE
        cents < 0 -> TunerUiState.HINT_FLAT
        else -> TunerUiState.HINT_SHARP
    }
    val testToneNote = if (testToneHz > 0.0) {
        NoteMath.name(NoteMath.nearestMidi(testToneHz, settings.a4Hz), notation)
    } else {
        "–"
    }
    return TunerUiState(
        tuningName = tuning.name,
        strings = strings,
        targetLabel = targetMidi?.let { NoteMath.name(it, notation) },
        targetLetter = targetMidi?.let { NoteMath.letter(it, notation) },
        targetOctave = targetMidi?.let { NoteMath.octave(it).toString() },
        centsOff = cents,
        centsLabel = cents?.let(::formatCents),
        inTune = inTune,
        hint = hint,
        pitchHz = engine.pitchHz,
        showHz = settings.showHz,
        autoMode = engine.autoMode,
        chromatic = chromatic,
        chromaticMidi = if (chromatic) engine.chromaticMidi else null,
        notation = notation,
        input = engine.input,
        inputCaption = inputCaption(engine.input),
        availableInputs = engine.availableInputs,
        selectedInputKey = selectedInputKey,
        permissionGranted = engine.permissionGranted,
        running = engine.running,
        error = engine.error,
        headstockLayout = settings.headstockLayout,
        keepScreenOn = settings.keepScreenOn,
        toleranceCents = settings.toleranceCents,
        testToneHz = testToneHz,
        testToneNote = testToneNote,
    )
}

/** "+3", "−12", "0" — a real minus sign, rounded to the nearest cent. */
fun formatCents(cents: Double): String {
    val rounded = cents.roundToInt()
    return when {
        rounded == 0 -> "0"
        rounded > 0 -> "+$rounded"
        else -> "−${-rounded}"
    }
}

/** "110.19 Hz", always two decimals and a dot regardless of locale. */
fun formatHz(hz: Double): String = String.format(Locale.US, "%.2f Hz", hz)

/** Short header caption for an input, by kind. The full product name lives in the input sheet. */
fun inputCaption(input: AudioInputDevice?): String = when (input?.kind) {
    null -> TunerUiState.CAPTION_NONE
    InputKind.BUILTIN_MIC -> "Mic"
    InputKind.USB -> "USB"
    InputKind.WIRED_HEADSET -> "Headset"
    InputKind.BLUETOOTH -> "Bluetooth"
    InputKind.OTHER -> "Input"
    InputKind.TEST_TONE -> "Test tone"
}
