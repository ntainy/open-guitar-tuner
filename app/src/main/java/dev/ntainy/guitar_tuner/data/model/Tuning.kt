package dev.ntainy.guitar_tuner.data.model

import dev.ntainy.guitar_tuner.dsp.NoteMath
import kotlinx.serialization.Serializable

/** The app tunes six-string guitars only. */
const val STRING_COUNT = 6

/** Lowest and highest MIDI note the editor allows per string (C1 .. C6). */
const val MIN_STRING_MIDI = 24
const val MAX_STRING_MIDI = 84

@Serializable
enum class TuningGroup(val label: String) {
    MINE("My tunings"),
    STANDARD("Standard"),
    POWER("Power"),
    TRANSPOSED("Transposed"),
    OPEN("Open"),
    EXTRAS("Extras"),
}

/**
 * A tuning: six MIDI notes, low string first (index 0 = low E on a standard guitar).
 *
 * Presets are immutable and ship with the app; custom tunings ([TuningGroup.MINE]) are unlimited and stored on disk.
 */
@Serializable
data class Tuning(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val strings: List<Int>,
    val isPreset: Boolean = false,
    val group: TuningGroup = TuningGroup.MINE,
    val createdAt: Long = 0L,
) {
    /** Target frequency of every string, low to high, for the given reference pitch. */
    fun frequencies(a4Hz: Double = NoteMath.DEFAULT_A4_HZ): DoubleArray =
        DoubleArray(strings.size) { NoteMath.frequency(strings[it], a4Hz) }

    /** Human-readable problems, empty when the tuning can be saved. */
    fun validationErrors(): List<String> = buildList {
        if (name.isBlank()) add("Name is empty")
        if (strings.size != STRING_COUNT) add("A tuning needs exactly $STRING_COUNT strings")
        strings.forEachIndexed { i, midi ->
            if (midi !in MIN_STRING_MIDI..MAX_STRING_MIDI) add("String ${i + 1} is out of range")
        }
    }

    val isValid: Boolean get() = validationErrors().isEmpty()
}

/** Ids of presets other code needs to refer to. The full catalog lives in `data/presets/PresetTunings.kt`. */
object PresetIds {
    const val STANDARD = "preset_standard"
    const val PREFIX = "preset_"
}
