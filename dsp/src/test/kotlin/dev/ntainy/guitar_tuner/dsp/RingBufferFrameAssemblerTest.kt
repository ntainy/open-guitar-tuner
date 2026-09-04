package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RingBufferFrameAssemblerTest {
    private val frameSize = DspDefaults.FRAME_SIZE
    private val hopSize = DspDefaults.HOP_SIZE

    @Test
    fun defaultsMatchDspDefaults() {
        val assembler = RingBufferFrameAssembler()
        assertEquals(DspDefaults.FRAME_SIZE, assembler.frameSize)
        assertEquals(DspDefaults.HOP_SIZE, assembler.hopSize)
    }

    @Test
    fun chunksOf1000YieldExpectedFrameCount() {
        val assembler = RingBufferFrameAssembler(frameSize, hopSize)
        val chunk = FloatArray(1000)
        var frames = 0
        var total = 0
        repeat(20) {
            assembler.push(chunk) { frames++ }
            total += chunk.size
            val expected = if (total < frameSize) 0 else (total - frameSize) / hopSize + 1
            assertEquals(expected, frames, "after $total samples")
        }
        assertEquals(8, frames)
    }

    @Test
    fun nothingIsEmittedBeforeTheFirstFullFrame() {
        val assembler = RingBufferFrameAssembler(frameSize, hopSize)
        var frames = 0
        assembler.push(FloatArray(frameSize - 1)) { frames++ }
        assertEquals(0, frames)
        assembler.push(FloatArray(1)) { frames++ }
        assertEquals(1, frames)
    }

    @Test
    fun framesOverlapByFrameMinusHop() {
        val assembler = RingBufferFrameAssembler(frameSize, hopSize)
        val signal = SyntheticSignals.ramp(frameSize + 5 * hopSize + 123)
        val frames = mutableListOf<FloatArray>()
        var offset = 0
        while (offset < signal.size) {
            val n = minOf(1000, signal.size - offset)
            assembler.push(signal.copyOfRange(offset, offset + n), n) { frames += it.copyOf() }
            offset += n
        }
        assertEquals(6, frames.size)
        frames.forEachIndexed { k, frame ->
            val expected = FloatArray(frameSize) { (k * hopSize + it).toFloat() }
            assertContentEquals(expected, frame, "frame $k")
        }
    }

    @Test
    fun oneChunkLargerThanSeveralFramesEmitsThemAllInOrder() {
        val assembler = RingBufferFrameAssembler(frameSize, hopSize)
        val signal = SyntheticSignals.ramp(frameSize + 3 * hopSize)
        val starts = mutableListOf<Float>()
        assembler.push(signal) { starts += it[0] }
        assertEquals(listOf(0f, hopSize.toFloat(), 2f * hopSize, 3f * hopSize), starts)
    }

    @Test
    fun countLimitsHowMuchOfTheChunkIsUsed() {
        val assembler = RingBufferFrameAssembler(frameSize = 8, hopSize = 4)
        val received = mutableListOf<FloatArray>()
        assembler.push(SyntheticSignals.ramp(100), count = 8) { received += it.copyOf() }
        assertEquals(1, received.size)
        assertContentEquals(FloatArray(8) { it.toFloat() }, received[0])
    }

    @Test
    fun callbackArrayIsReused() {
        val assembler = RingBufferFrameAssembler(frameSize = 8, hopSize = 4)
        val seen = mutableListOf<FloatArray>()
        assembler.push(SyntheticSignals.ramp(16)) { seen += it }
        assertEquals(3, seen.size)
        assertSame(seen[0], seen[1])
        assertSame(seen[1], seen[2])
    }

    @Test
    fun hopEqualToFrameGivesNonOverlappingFrames() {
        val assembler = RingBufferFrameAssembler(frameSize = 8, hopSize = 8)
        val starts = mutableListOf<Float>()
        assembler.push(SyntheticSignals.ramp(24)) { starts += it[0] }
        assertEquals(listOf(0f, 8f, 16f), starts)
    }

    @Test
    fun resetStartsAFreshFrame() {
        val assembler = RingBufferFrameAssembler(frameSize, hopSize)
        var frames = 0
        assembler.push(FloatArray(frameSize + hopSize - 1)) { frames++ }
        assertEquals(1, frames)

        assembler.reset()
        assembler.push(FloatArray(frameSize - 1)) { frames++ }
        assertEquals(1, frames, "reset must discard the partially filled hop")

        val firstAfterReset = mutableListOf<FloatArray>()
        assembler.push(SyntheticSignals.ramp(1)) { firstAfterReset += it.copyOf() }
        assertEquals(1, firstAfterReset.size)
        assertEquals(0f, firstAfterReset[0][frameSize - 1])
        assertTrue(firstAfterReset[0].dropLast(1).all { it == 0f })
    }

    @Test
    fun rejectsInvalidGeometry() {
        assertFailsWith<IllegalArgumentException> { RingBufferFrameAssembler(frameSize = 0, hopSize = 1) }
        assertFailsWith<IllegalArgumentException> { RingBufferFrameAssembler(frameSize = 8, hopSize = 0) }
        assertFailsWith<IllegalArgumentException> { RingBufferFrameAssembler(frameSize = 8, hopSize = 9) }
        assertFailsWith<IllegalArgumentException> {
            RingBufferFrameAssembler(frameSize = 8, hopSize = 4).push(FloatArray(4), count = 5) {}
        }
    }
}
