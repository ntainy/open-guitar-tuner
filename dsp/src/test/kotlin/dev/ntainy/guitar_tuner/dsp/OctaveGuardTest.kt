package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OctaveGuardTest {
    private fun e(hz: Double) = PitchEstimate(hz, 0.9, 0.01)

    @Test
    fun octaveDownErrorInTheDecayIsFoldedBack() {
        val g = OctaveGuard()
        g.apply(e(109.4))
        assertEquals(109.3, g.apply(e(54.65))!!.frequencyHz, 0.05)
        assertEquals(109.2, g.apply(e(54.6))!!.frequencyHz, 0.05)
    }

    @Test
    fun twoOctavesDownIsFoldedToo() {
        val g = OctaveGuard()
        g.apply(e(195.6))
        assertEquals(195.6, g.apply(e(48.9))!!.frequencyHz, 0.1)
    }

    @Test
    fun octaveUpIsFoldedDown() {
        val g = OctaveGuard()
        g.apply(e(196.2))
        assertEquals(196.15, g.apply(e(392.3))!!.frequencyHz, 0.05)
    }

    @Test
    fun honestReadingsAreUntouchedAndMoveTheReference() {
        val g = OctaveGuard()
        g.apply(e(82.0))
        assertEquals(82.6, g.apply(e(82.6))!!.frequencyHz, 1e-9)
        assertEquals(82.6, g.referenceHz, 1e-9)
        assertEquals(146.8, g.apply(e(146.8))!!.frequencyHz, 1e-9, "a genuinely different pitch passes")
    }

    @Test
    fun resetAcceptsAnOctaveOfTheOldNoteAsNew() {
        val g = OctaveGuard()
        g.apply(e(82.4))
        g.reset()
        assertEquals(329.6, g.apply(e(329.6))!!.frequencyHz, 1e-9)
    }

    @Test
    fun nullPassesThrough() {
        val g = OctaveGuard()
        g.apply(e(100.0))
        assertNull(g.apply(null))
    }
}
