package dev.ntainy.guitar_tuner.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * Adaptive level gate: a frame is trusted only when it is clearly louder than the recent noise floor.
 *
 * Phone microphones pick up a faint low-frequency hum (mains, handling, HVAC) that a pitch detector will happily
 * report as a "note" around 40–55 Hz once the guitar has faded. The floor is the quietest one-second bucket seen
 * in the last [buckets] seconds, so a pause between plucks is enough to learn it, and a louder room is
 * re-learned within [buckets] seconds. A frame passes when its RMS is at least `max(minRms, ratio × floor)`,
 * capped at [maxThreshold] so a continuous signal (a sustained tone, a synthetic test tone) can never gate itself.
 *
 * Feed every capture chunk to [observe]; ask [passes] for the frames you want to trust. Levels are linear RMS
 * with 1.0 = full scale.
 *
 * @param minRms absolute floor of the threshold
 * @param ratio how many times louder than the noise floor a frame must be
 * @param maxThreshold ceiling of the threshold; above this level everything passes regardless of the floor
 * @param chunksPerBucket capture chunks per bucket (47 ≈ one second of 1024-sample chunks at 48 kHz)
 * @param buckets how many buckets the floor looks back over
 */
class NoiseGate(
    private val minRms: Double = 0.004,
    private val ratio: Double = 2.0,
    private val maxThreshold: Double = 0.02,
    private val chunksPerBucket: Int = 47,
    private val buckets: Int = 8,
) {
    init {
        require(minRms >= 0.0) { "minRms must not be negative, was $minRms" }
        require(ratio >= 1.0) { "ratio must be at least 1, was $ratio" }
        require(maxThreshold >= minRms) { "maxThreshold must not be below minRms, was $maxThreshold" }
        require(chunksPerBucket >= 1) { "chunksPerBucket must be positive, was $chunksPerBucket" }
        require(buckets >= 1) { "buckets must be positive, was $buckets" }
    }

    private val minima = DoubleArray(buckets) { Double.NaN }
    private var bucket = 0
    private var chunksInBucket = 0

    /** Quietest level seen over the window, or NaN before the first observation. */
    val floor: Double
        get() {
            var f = Double.NaN
            for (m in minima) if (!m.isNaN() && (f.isNaN() || m < f)) f = m
            return f
        }

    /** Level a frame must reach to pass. */
    val threshold: Double
        get() {
            val f = floor
            return if (f.isNaN()) minRms else max(minRms, min(f * ratio, maxThreshold))
        }

    /** Record the RMS of one capture chunk. */
    fun observe(rms: Double) {
        if (chunksInBucket >= chunksPerBucket) {
            bucket = (bucket + 1) % buckets
            minima[bucket] = Double.NaN
            chunksInBucket = 0
        }
        val m = minima[bucket]
        if (m.isNaN() || rms < m) minima[bucket] = rms
        chunksInBucket++
    }

    /** True when a frame at this RMS is clearly above the noise floor. */
    fun passes(rms: Double): Boolean = rms >= threshold

    fun reset() {
        minima.fill(Double.NaN)
        bucket = 0
        chunksInBucket = 0
    }
}
