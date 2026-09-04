package dev.ntainy.guitar_tuner.ui.editor

import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.dsp.NoteMath

/**
 * The arithmetic behind the two-step note picker: a note is a pitch class plus an octave, and only the
 * combinations inside `MIN_STRING_MIDI..MAX_STRING_MIDI` (C1 to C6) are offered.
 */
internal object NotePickerRange {
    /** Pitch classes in chip order, C first. */
    val pitchClasses: IntRange = 0..11

    /** Every octave that holds at least one allowed note: the octave of C1 through the octave of C6. */
    val octaves: IntRange = NoteMath.octave(MIN_STRING_MIDI)..NoteMath.octave(MAX_STRING_MIDI)

    /** MIDI number of [pitchClass] in scientific [octave]: (0, 4) is C4 = 60, (4, 2) is E2 = 40. */
    fun midi(pitchClass: Int, octave: Int): Int = (octave + 1) * 12 + pitchClass

    /** Whether the editor allows a string at this MIDI note. */
    fun isAllowed(midi: Int): Boolean = midi in MIN_STRING_MIDI..MAX_STRING_MIDI

    /** Whether picking [pitchClass] while [octave] is selected lands on an allowed note. */
    fun isAllowed(pitchClass: Int, octave: Int): Boolean = isAllowed(midi(pitchClass, octave))
}
