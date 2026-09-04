package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HarmonicsTest {
    private val g3 = NoteMath.frequency(55)
    private val e2 = NoteMath.frequency(40)

    @Test
    fun fundamentalIsFoldOne() {
        assertEquals(1, Harmonics.bestFold(g3 * 1.01, g3))
    }

    @Test
    fun octaveAboveFoldsToTwo() {
        assertEquals(2, Harmonics.bestFold(392.3, g3))
        assertEquals(1.3, Harmonics.cents(392.3, g3, 2), 0.2)
    }

    @Test
    fun thirdAndFourthOvertonesFold() {
        assertEquals(3, Harmonics.bestFold(e2 * 3 * 1.002, e2))
        assertEquals(4, Harmonics.bestFold(e2 * 4, e2))
    }

    @Test
    fun farFromAnyMultipleStaysFoldOne() {
        assertEquals(1, Harmonics.bestFold(e2 * 2.7, e2))
        assertEquals(1, Harmonics.bestFold(e2 * 0.5, e2))
    }

    @Test
    fun penaltiesGrowWithFold() {
        assertTrue(Harmonics.penaltyCents(1) < Harmonics.penaltyCents(2))
        assertTrue(Harmonics.penaltyCents(2) < Harmonics.penaltyCents(3))
        assertTrue(Harmonics.penaltyCents(3) < Harmonics.penaltyCents(4))
        assertEquals(0.0, Harmonics.penaltyCents(1), 0.0)
    }
}
