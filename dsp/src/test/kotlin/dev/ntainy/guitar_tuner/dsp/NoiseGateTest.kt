package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoiseGateTest {
    private fun gate() = NoiseGate(minRms = 0.004, ratio = 2.0, chunksPerBucket = 4, buckets = 3)

    @Test
    fun absoluteMinimumAppliesBeforeAnyObservation() {
        val g = gate()
        assertEquals(0.004, g.threshold, 1e-12)
        assertFalse(g.passes(0.003))
        assertTrue(g.passes(0.004))
    }

    @Test
    fun floorIsTheQuietestChunkAndThresholdScalesWithIt() {
        val g = gate()
        listOf(0.003, 0.0025, 0.03, 0.02).forEach(g::observe)
        assertEquals(0.0025, g.floor, 1e-12)
        assertEquals(0.005, g.threshold, 1e-12)
        assertFalse(g.passes(0.0045))
        assertTrue(g.passes(0.0051))
    }

    @Test
    fun hummingRoomGatesTheHumButNotTheGuitar() {
        val g = gate()
        repeat(12) { g.observe(0.003) }
        assertFalse(g.passes(0.0045), "a 45 Hz hum at 1.5x floor must not pass")
        assertTrue(g.passes(0.01), "a decaying note at 3x floor must pass")
    }

    @Test
    fun louderRoomIsRelearnedOnceOldBucketsExpire() {
        val g = gate()
        repeat(4) { g.observe(0.002) }
        repeat(12) { g.observe(0.008) }
        assertEquals(0.008, g.floor, 1e-12)
        assertEquals(0.016, g.threshold, 1e-12)
    }

    @Test
    fun continuousSignalCanNeverGateItself() {
        val g = NoiseGate(minRms = 0.004, ratio = 2.0, maxThreshold = 0.02, chunksPerBucket = 4, buckets = 3)
        repeat(20) { g.observe(0.18) }
        assertEquals(0.02, g.threshold, 1e-12)
        assertTrue(g.passes(0.18), "a sustained tone at full level must pass")
        assertTrue(g.passes(0.05), "and so must any note louder than the ceiling")
    }

    @Test
    fun aRingingNoteDoesNotRaiseTheFloorWhileQuietBucketsRemain() {
        val g = gate()
        repeat(4) { g.observe(0.002) }
        repeat(7) { g.observe(0.05) }
        assertEquals(0.002, g.floor, 1e-12)
    }

    @Test
    fun resetForgetsTheFloor() {
        val g = gate()
        repeat(4) { g.observe(0.01) }
        g.reset()
        assertEquals(0.004, g.threshold, 1e-12)
    }
}
