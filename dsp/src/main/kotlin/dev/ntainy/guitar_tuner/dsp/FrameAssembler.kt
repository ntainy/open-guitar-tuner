package dev.ntainy.guitar_tuner.dsp

/**
 * Turns arbitrarily sized capture chunks into fixed-size, overlapping analysis frames.
 *
 * Every [hopSize] new samples a frame of the last [frameSize] samples is delivered to the callback.
 * The array handed to the callback is owned by the assembler and only valid during the callback.
 * Wave 1 implementation: [RingBufferFrameAssembler].
 */
interface FrameAssembler {
    val frameSize: Int
    val hopSize: Int

    fun push(samples: FloatArray, count: Int = samples.size, onFrame: (FloatArray) -> Unit)

    fun reset()
}
