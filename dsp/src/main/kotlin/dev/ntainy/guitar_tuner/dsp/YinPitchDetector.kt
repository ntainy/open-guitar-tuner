package dev.ntainy.guitar_tuner.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Time-domain YIN fundamental-frequency estimator (de Cheveigné & Kawahara, 2002).
 *
 * For each frame:
 * 1. Level gate. The AC RMS of the frame must reach [silenceRms], otherwise the frame counts as silence and
 *    `null` is returned. The DC offset is removed in the same pass.
 * 2. Difference function `d(τ) = Σ_j (x_j − x_{j+τ})²` over a window of [windowSize] samples for
 *    `τ = 1..maxLag`. Lags are evaluated lazily, in increasing order, so the search below can stop early.
 * 3. Cumulative mean normalised difference `d'(τ) = d(τ) / ((1/τ) Σ_{k≤τ} d(k))`.
 * 4. Absolute threshold. Starting at the first lag in `[minLag, maxLag]` whose `d'` drops below [threshold],
 *    the lowest point of that contiguous sub-threshold region is chosen (more robust than stopping at the first
 *    local minimum when noise ripples a broad valley). If no lag dips below [threshold], the global minimum over
 *    the lag range is accepted when it lies below [fallbackThreshold] (with correspondingly lower confidence);
 *    otherwise `null`.
 * 5. Parabolic interpolation of `d` around the chosen lag gives a fractional period; frequency = sampleRate / period.
 *
 * Confidence is `1 − d'(τ)` at the chosen lag. All work buffers are allocated once; [detect] allocates nothing but
 * the returned [PitchEstimate]. Not thread-safe: use one instance per analysis thread.
 *
 * @param sampleRate capture sample rate in Hz
 * @param frameSize samples per frame; must be at least twice the longest lag
 * @param minFrequencyHz lowest detectable fundamental (sets the longest lag)
 * @param maxFrequencyHz highest detectable fundamental (sets the shortest lag)
 * @param threshold YIN absolute threshold on `d'`
 * @param silenceRms frames with an AC RMS below this return `null`
 * @param fallbackThreshold when no lag dips below [threshold], accept the global minimum of `d'` if it is below
 *   this; pass 0.0 to disable the fallback
 */
class YinPitchDetector(
    override val sampleRate: Int = DspDefaults.SAMPLE_RATE,
    override val frameSize: Int = DspDefaults.FRAME_SIZE,
    minFrequencyHz: Double = DspDefaults.MIN_FREQUENCY_HZ,
    maxFrequencyHz: Double = DspDefaults.MAX_FREQUENCY_HZ,
    private val threshold: Double = DspDefaults.YIN_THRESHOLD,
    private val silenceRms: Double = DspDefaults.SILENCE_RMS,
    private val fallbackThreshold: Double = DEFAULT_FALLBACK_THRESHOLD,
) : PitchDetector {
    /** Shortest lag searched, `floor(sampleRate / maxFrequencyHz)`. */
    val minLag: Int = floor(sampleRate / maxFrequencyHz).toInt()

    /** Longest lag searched, `ceil(sampleRate / minFrequencyHz)`. */
    val maxLag: Int = ceil(sampleRate / minFrequencyHz).toInt()

    /** Samples integrated per lag: `frameSize − maxLag`, so every lag uses the same window. */
    val windowSize: Int = frameSize - maxLag

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(minFrequencyHz > 0.0 && maxFrequencyHz > minFrequencyHz) {
            "need 0 < minFrequencyHz < maxFrequencyHz, got $minFrequencyHz..$maxFrequencyHz"
        }
        require(minLag >= 1) { "maxFrequencyHz $maxFrequencyHz is above sampleRate / 2" }
        require(frameSize >= 2 * maxLag) {
            "frameSize $frameSize must be at least twice the longest lag ($maxLag samples for $minFrequencyHz Hz)"
        }
        require(threshold > 0.0) { "threshold must be positive, was $threshold" }
        require(fallbackThreshold >= 0.0) { "fallbackThreshold must not be negative, was $fallbackThreshold" }
    }

    private val samples = DoubleArray(frameSize)
    private val difference = DoubleArray(maxLag + 1)
    private val normalised = DoubleArray(maxLag + 1)
    private var runningSum = 0.0

    override fun detect(frame: FloatArray): PitchEstimate? {
        require(frame.size >= frameSize) { "frame has ${frame.size} samples, need $frameSize" }

        var sum = 0.0
        var sumOfSquares = 0.0
        for (i in 0 until frameSize) {
            val v = frame[i].toDouble()
            sum += v
            sumOfSquares += v * v
        }
        val mean = sum / frameSize
        val rms = sqrt(max(sumOfSquares / frameSize - mean * mean, 0.0))
        if (rms < silenceRms) return null
        for (i in 0 until frameSize) samples[i] = frame[i] - mean

        runningSum = 0.0
        normalised[0] = 1.0
        var tau = 1
        while (tau < minLag) {
            computeLag(tau)
            tau++
        }

        var best = -1
        while (tau <= maxLag) {
            if (computeLag(tau) < threshold) {
                // Inside the first dip: take the lowest point of the whole region below the threshold rather than
                // the first local minimum, which noise on a broad (sine-like) valley would otherwise stop at early.
                best = tau
                while (tau < maxLag) {
                    val next = computeLag(tau + 1)
                    tau++
                    if (next >= threshold) break
                    if (next < normalised[best]) best = tau
                }
                break
            }
            tau++
        }

        if (best < 0) {
            var minimum = minLag
            for (t in minLag + 1..maxLag) if (normalised[t] < normalised[minimum]) minimum = t
            if (normalised[minimum] >= fallbackThreshold) return null
            best = minimum
        }

        val period = best + parabolicOffset(best)
        val confidence = (1.0 - normalised[best]).coerceIn(0.0, 1.0)
        return PitchEstimate(frequencyHz = sampleRate / period, confidence = confidence, rms = rms)
    }

    /**
     * Computes `d(tau)` and `d'(tau)`. Lags must be requested in increasing order from 1 because the normalisation
     * depends on the running sum of the previous lags. Returns `d'(tau)`.
     */
    private fun computeLag(tau: Int): Double {
        val x = samples
        val w = windowSize
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        var s3 = 0.0
        var j = 0
        val unrolledEnd = w - (w and 3)
        while (j < unrolledEnd) {
            val a = x[j] - x[j + tau]
            val b = x[j + 1] - x[j + 1 + tau]
            val c = x[j + 2] - x[j + 2 + tau]
            val d = x[j + 3] - x[j + 3 + tau]
            s0 += a * a
            s1 += b * b
            s2 += c * c
            s3 += d * d
            j += 4
        }
        while (j < w) {
            val a = x[j] - x[j + tau]
            s0 += a * a
            j++
        }
        val d = (s0 + s1) + (s2 + s3)
        difference[tau] = d
        runningSum += d
        val n = if (runningSum > 0.0) d * tau / runningSum else 1.0
        normalised[tau] = n
        return n
    }

    /** Sub-sample offset of the minimum of the parabola through `d(tau−1)`, `d(tau)`, `d(tau+1)`; 0 at the edges. */
    private fun parabolicOffset(tau: Int): Double {
        if (tau <= 1 || tau >= maxLag) return 0.0
        val left = difference[tau - 1]
        val centre = difference[tau]
        val right = difference[tau + 1]
        val denominator = left - 2.0 * centre + right
        if (denominator <= 0.0) return 0.0
        return (0.5 * (left - right) / denominator).coerceIn(-1.0, 1.0)
    }

    companion object {
        /** Global-minimum acceptance level used when nothing crosses the primary threshold. */
        const val DEFAULT_FALLBACK_THRESHOLD = 0.3
    }
}
