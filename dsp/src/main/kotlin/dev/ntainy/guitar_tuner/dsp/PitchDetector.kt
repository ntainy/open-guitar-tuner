package dev.ntainy.guitar_tuner.dsp

/**
 * One pitch reading for a single analysis frame.
 *
 * @property frequencyHz estimated fundamental frequency
 * @property confidence 0..1, higher is more periodic (for YIN: 1 - aperiodicity at the chosen lag)
 * @property rms root-mean-square level of the frame, 0..1 for full-scale float input
 */
data class PitchEstimate(val frequencyHz: Double, val confidence: Double, val rms: Double)

/**
 * Estimates the fundamental frequency of one frame of mono float samples in [-1, 1].
 *
 * Implementations are stateless per call and must be cheap enough to run ~25 times per second on a phone.
 * Wave 1 implementation: [YinPitchDetector].
 */
interface PitchDetector {
    val sampleRate: Int

    /** Number of samples a frame passed to [detect] must have. */
    val frameSize: Int

    /** Returns null when the frame is silent or not periodic enough to trust. */
    fun detect(frame: FloatArray): PitchEstimate?
}

/** Shared defaults for the analysis chain. Tune here, not in call sites. */
object DspDefaults {
    const val SAMPLE_RATE = 48_000
    const val FRAME_SIZE = 4096
    const val HOP_SIZE = 2048
    const val MIN_FREQUENCY_HZ = 40.0
    const val MAX_FREQUENCY_HZ = 1200.0
    const val YIN_THRESHOLD = 0.15

    /** Frames whose RMS is below this are treated as silence. Full scale is 1.0. */
    const val SILENCE_RMS = 0.003
}
