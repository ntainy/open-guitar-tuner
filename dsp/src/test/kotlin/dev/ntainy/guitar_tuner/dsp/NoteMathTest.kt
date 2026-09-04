package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteMathTest {
    private val eps = 0.01

    @Test
    fun a4Is440() {
        assertEquals(440.0, NoteMath.frequency(69), eps)
    }

    @Test
    fun standardTuningFrequencies() {
        val expected = doubleArrayOf(82.41, 110.00, 146.83, 196.00, 246.94, 329.63)
        val midi = intArrayOf(40, 45, 50, 55, 59, 64)
        midi.forEachIndexed { i, m -> assertEquals(expected[i], NoteMath.frequency(m), eps) }
    }

    @Test
    fun referencePitchScalesEverything() {
        assertEquals(432.0, NoteMath.frequency(69, a4Hz = 432.0), eps)
        assertEquals(82.41 * 432.0 / 440.0, NoteMath.frequency(40, a4Hz = 432.0), eps)
    }

    @Test
    fun centsAreSignedAndLogarithmic() {
        assertEquals(0.0, NoteMath.cents(440.0, 440.0), 1e-9)
        assertEquals(100.0, NoteMath.cents(NoteMath.frequency(70), 440.0), 1e-9)
        assertEquals(-100.0, NoteMath.cents(NoteMath.frequency(68), 440.0), 1e-9)
        assertEquals(50.0, NoteMath.cents(NoteMath.frequency(69.5), 69), 1e-9)
    }

    @Test
    fun nearestMidiRoundsToClosestNote() {
        assertEquals(40, NoteMath.nearestMidi(82.4))
        assertEquals(40, NoteMath.nearestMidi(84.0))
        assertEquals(41, NoteMath.nearestMidi(86.0))
        assertEquals(69, NoteMath.nearestMidi(440.0))
    }

    @Test
    fun namesWithSharpsAndFlats() {
        assertEquals("E2", NoteMath.name(40))
        assertEquals("A♯2", NoteMath.name(46, Notation.SHARPS))
        assertEquals("B♭2", NoteMath.name(46, Notation.FLATS))
        assertEquals("C4", NoteMath.name(60))
        assertEquals("B3", NoteMath.name(59))
        assertEquals("E4", NoteMath.name(64))
        assertEquals("C♯", NoteMath.letter(61))
        assertEquals("D♭", NoteMath.letter(61, Notation.FLATS))
    }

    @Test
    fun octaveBoundaries() {
        assertEquals(4, NoteMath.octave(60))
        assertEquals(3, NoteMath.octave(59))
        assertEquals(-1, NoteMath.octave(0))
        assertEquals(0, NoteMath.pitchClass(60))
        assertEquals(11, NoteMath.pitchClass(59))
    }
}
