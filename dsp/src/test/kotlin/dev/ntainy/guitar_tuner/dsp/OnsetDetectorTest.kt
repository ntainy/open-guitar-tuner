package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnsetDetectorTest {
    @Test
    fun firstSoundAfterSilenceIsAnOnset() {
        val d = OnsetDetector()
        repeat(50) { assertFalse(d.push(0.002)) }
        assertTrue(d.push(0.05))
    }

    @Test
    fun aRingingNoteIsNotRepeatedlyDetected() {
        val d = OnsetDetector()
        assertTrue(d.push(0.08))
        var level = 0.08
        repeat(200) {
            level *= 0.995 // decays no faster than the follower
            assertFalse(d.push(level), "decaying note flagged as onset at chunk $it")
        }
    }

    @Test
    fun rePluckWhileRingingIsAnOnset() {
        val d = OnsetDetector()
        d.push(0.08)
        var level = 0.08
        repeat(100) { level *= 0.99; d.push(level) }
        assertTrue(d.push(0.08))
    }

    @Test
    fun nextStringBarelyLouderIsNotAnOnsetButResetIsAvailable() {
        val d = OnsetDetector()
        d.push(0.05)
        assertFalse(d.push(0.06))
        d.reset()
        assertEquals(0.0, d.peak)
        assertTrue(d.push(0.06))
    }
}
