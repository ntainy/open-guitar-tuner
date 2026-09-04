package dev.ntainy.guitar_tuner.dsp

import kotlin.math.min

/**
 * [FrameAssembler] backed by a fixed ring buffer of [frameSize] samples.
 *
 * Capture chunks of any size are appended to the ring. Once [frameSize] samples have arrived the first frame is
 * emitted, and after that a new frame is emitted every [hopSize] samples, each containing the most recent
 * [frameSize] samples (so consecutive frames overlap by `frameSize - hopSize` samples). The array handed to
 * `onFrame` is a single preallocated scratch buffer that is overwritten by the next frame; copy it if it must
 * outlive the callback.
 *
 * All buffers are allocated in the constructor; [push] does not allocate. Not thread-safe.
 */
class RingBufferFrameAssembler(
    override val frameSize: Int = DspDefaults.FRAME_SIZE,
    override val hopSize: Int = DspDefaults.HOP_SIZE,
) : FrameAssembler {
    init {
        require(frameSize > 0) { "frameSize must be positive, was $frameSize" }
        require(hopSize in 1..frameSize) { "hopSize must be in 1..frameSize, was $hopSize (frameSize $frameSize)" }
    }

    private val ring = FloatArray(frameSize)
    private val frame = FloatArray(frameSize)

    /** Index of the next write; the oldest sample of the ring lives at the same index. */
    private var writePos = 0

    /** Samples still needed before the next frame is due. */
    private var untilNextFrame = frameSize

    override fun push(samples: FloatArray, count: Int, onFrame: (FloatArray) -> Unit) {
        require(count in 0..samples.size) { "count $count out of range for a chunk of ${samples.size}" }
        var offset = 0
        var remaining = count
        while (remaining > 0) {
            val n = min(remaining, untilNextFrame)
            append(samples, offset, n)
            offset += n
            remaining -= n
            untilNextFrame -= n
            if (untilNextFrame == 0) {
                unwrapInto(frame)
                onFrame(frame)
                untilNextFrame = hopSize
            }
        }
    }

    override fun reset() {
        writePos = 0
        untilNextFrame = frameSize
    }

    /** Copies [n] samples (never more than [frameSize]) into the ring, wrapping at most once. */
    private fun append(source: FloatArray, offset: Int, n: Int) {
        val first = min(n, frameSize - writePos)
        System.arraycopy(source, offset, ring, writePos, first)
        val second = n - first
        if (second > 0) System.arraycopy(source, offset + first, ring, 0, second)
        writePos += n
        if (writePos >= frameSize) writePos -= frameSize
    }

    /** Lays the ring out chronologically (oldest sample first) into [target]. */
    private fun unwrapInto(target: FloatArray) {
        val tail = frameSize - writePos
        System.arraycopy(ring, writePos, target, 0, tail)
        System.arraycopy(ring, 0, target, tail, writePos)
    }
}
