package dev.ntainy.guitar_tuner.dsp

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** How accidentals are spelled when a note is named. */
enum class Notation { SHARPS, FLATS }

/**
 * Conversions between MIDI note numbers, frequencies and cents.
 *
 * MIDI 69 = A4. MIDI 60 = C4. Standard guitar tuning low to high is 40 45 50 55 59 64 (E2 A2 D3 G3 B3 E4).
 */
object NoteMath {
    const val A4_MIDI = 69
    const val DEFAULT_A4_HZ = 440.0

    private val SHARP_LETTERS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val FLAT_LETTERS = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
    private val LN2 = ln(2.0)

    /** Frequency in Hz of a (possibly fractional) MIDI note for the given reference pitch. */
    fun frequency(midi: Double, a4Hz: Double = DEFAULT_A4_HZ): Double = a4Hz * 2.0.pow((midi - A4_MIDI) / 12.0)

    fun frequency(midi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double = frequency(midi.toDouble(), a4Hz)

    /** Fractional MIDI number of a frequency, e.g. 440 Hz -> 69.0, 445 Hz -> 69.196. */
    fun midiFromFrequency(hz: Double, a4Hz: Double = DEFAULT_A4_HZ): Double {
        require(hz > 0.0) { "frequency must be positive, was $hz" }
        return A4_MIDI + 12.0 * log2(hz / a4Hz)
    }

    /** Nearest MIDI note to a frequency. */
    fun nearestMidi(hz: Double, a4Hz: Double = DEFAULT_A4_HZ): Int = midiFromFrequency(hz, a4Hz).roundToInt()

    /** Signed offset of [hz] from [targetHz] in cents; positive means sharp. */
    fun cents(hz: Double, targetHz: Double): Double {
        require(hz > 0.0 && targetHz > 0.0) { "frequencies must be positive: $hz, $targetHz" }
        return 1200.0 * log2(hz / targetHz)
    }

    /** Signed offset of [hz] from the MIDI note [targetMidi] in cents. */
    fun cents(hz: Double, targetMidi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double = cents(hz, frequency(targetMidi, a4Hz))

    /** 0..11, where 0 = C. */
    fun pitchClass(midi: Int): Int = Math.floorMod(midi, 12)

    /** Scientific octave number: MIDI 60 -> 4, MIDI 40 -> 2. */
    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** Letter with accidental, no octave: MIDI 46 -> "A♯" or "B♭". */
    fun letter(midi: Int, notation: Notation = Notation.SHARPS): String =
        when (notation) {
            Notation.SHARPS -> SHARP_LETTERS[pitchClass(midi)]
            Notation.FLATS -> FLAT_LETTERS[pitchClass(midi)]
        }

    /** Full name with octave: MIDI 40 -> "E2", MIDI 46 -> "A♯2". */
    fun name(midi: Int, notation: Notation = Notation.SHARPS): String = letter(midi, notation) + octave(midi)

    fun log2(x: Double): Double = ln(x) / LN2
}
