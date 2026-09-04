package dev.ntainy.guitar_tuner.dsp

/**
 * Stabilises a stream of per-frame estimates into a display frequency.
 *
 * Expected behaviour: reject isolated outliers (octave jumps that last a frame or two), smooth jitter, and
 * report null after a short run of empty frames so the UI can show "no signal".
 * Wave 1 implementation: [MedianEmaSmoother].
 */
interface PitchSmoother {
    /** Feed the next frame's estimate (or null for silence). Returns the smoothed frequency in Hz, or null. */
    fun push(estimate: PitchEstimate?): Double?

    fun reset()
}
