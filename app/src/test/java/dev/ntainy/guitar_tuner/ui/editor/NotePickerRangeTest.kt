package dev.ntainy.guitar_tuner.ui.editor

import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.dsp.NoteMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotePickerRangeTest {
    @Test
    fun octavesRunFromC1ToC6() {
        assertEquals(1..6, NotePickerRange.octaves)
        assertEquals(0..11, NotePickerRange.pitchClasses)
    }

    @Test
    fun midiCombinesPitchClassAndScientificOctave() {
        assertEquals(60, NotePickerRange.midi(pitchClass = 0, octave = 4))
        assertEquals(40, NotePickerRange.midi(pitchClass = 4, octave = 2))
        assertEquals(69, NotePickerRange.midi(pitchClass = 9, octave = 4))
        assertEquals(MIN_STRING_MIDI, NotePickerRange.midi(pitchClass = 0, octave = 1))
        assertEquals(MAX_STRING_MIDI, NotePickerRange.midi(pitchClass = 0, octave = 6))
    }

    @Test
    fun midiRoundTripsThroughNoteMathForEveryAllowedNote() {
        for (midi in MIN_STRING_MIDI..MAX_STRING_MIDI) {
            assertEquals(midi, NotePickerRange.midi(NoteMath.pitchClass(midi), NoteMath.octave(midi)))
            assertTrue(NotePickerRange.isAllowed(midi), "$midi should be allowed")
        }
    }

    @Test
    fun notesOutsideTheEditorRangeAreNotAllowed() {
        assertFalse(NotePickerRange.isAllowed(MIN_STRING_MIDI - 1))
        assertFalse(NotePickerRange.isAllowed(MAX_STRING_MIDI + 1))
        assertFalse(NotePickerRange.isAllowed(pitchClass = 11, octave = 0))
    }

    @Test
    fun theTopOctaveOnlyOffersC() {
        val top = NotePickerRange.octaves.last
        assertTrue(NotePickerRange.isAllowed(pitchClass = 0, octave = top))
        for (pc in 1..11) assertFalse(NotePickerRange.isAllowed(pc, top), "pitch class $pc in octave $top")
    }

    @Test
    fun theBottomOctaveOffersEveryPitchClass() {
        val bottom = NotePickerRange.octaves.first
        for (pc in NotePickerRange.pitchClasses) assertTrue(NotePickerRange.isAllowed(pc, bottom), "pitch class $pc")
    }
}
