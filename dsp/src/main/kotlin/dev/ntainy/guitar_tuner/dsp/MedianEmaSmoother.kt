package dev.ntainy.guitar_tuner.dsp

import kotlin.math.abs

/**
 * [PitchSmoother] that works in the log-frequency (cents) domain: a short median filter to drop single-frame
 * outliers, a persistence gate against large jumps, and an exponential moving average for jitter.
 *
 * Per estimate:
 * - `null` estimates, and estimates whose confidence is below [minConfidence], count as empty frames. The last
 *   smoothed value is held for up to `nullFramesToReset − 1` empty frames; on the [nullFramesToReset]-th the
 *   smoother resets and returns `null`.
 * - A value further than [jumpCents] from the current smoothed value is ignored until [jumpFrames] such values in
 *   a row agree with each other within [jumpSpreadCents]; then the smoother jumps to their median (a genuine
 *   string change). A pluck's attack transient (a few scattered readings) therefore never gets through.
 * - Otherwise the value joins a window of the last [medianWindow] accepted values, and the window's median is fed
 *   to the EMA: `smoothed += alpha · (median − smoothed)`. When the median has moved more than [snapCents] from
 *   the smoothed value (a string change or the end of a pluck's attack transient) the output snaps to the median
 *   instead of easing towards it, so no intermediate pitch is ever shown.
 *
 * Output is `440 · 2^(cents / 1200)` of the smoothed cents value. Nothing is allocated after construction.
 *
 * @param medianWindow odd number of accepted values whose median feeds the EMA
 * @param jumpCents distance from the smoothed value beyond which a reading is treated as an outlier
 * @param jumpFrames consecutive outliers needed before the smoother follows them
 * @param alpha EMA coefficient in (0, 1]; 1 disables the averaging
 * @param nullFramesToReset consecutive empty frames after which the output becomes `null`
 * @param minConfidence estimates below this confidence count as empty frames
 * @param snapCents median-to-smoothed distance beyond which the EMA is bypassed
 * @param jumpSpreadCents how closely the [jumpFrames] outliers must agree before the smoother follows them
 */
class MedianEmaSmoother(
    private val medianWindow: Int = 3,
    private val jumpCents: Double = 80.0,
    private val jumpFrames: Int = 3,
    private val alpha: Double = 0.45,
    private val nullFramesToReset: Int = 4,
    private val minConfidence: Double = 0.5,
    private val snapCents: Double = 25.0,
    private val jumpSpreadCents: Double = 40.0,
) : PitchSmoother {
    init {
        require(medianWindow >= 1 && medianWindow % 2 == 1) {
            "medianWindow must be odd and positive, was $medianWindow"
        }
        require(jumpCents >= 0.0) { "jumpCents must not be negative, was $jumpCents" }
        require(jumpFrames >= 1) { "jumpFrames must be positive, was $jumpFrames" }
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1], was $alpha" }
        require(nullFramesToReset >= 1) { "nullFramesToReset must be positive, was $nullFramesToReset" }
        require(snapCents >= 0.0) { "snapCents must not be negative, was $snapCents" }
        require(jumpSpreadCents >= 0.0) { "jumpSpreadCents must not be negative, was $jumpSpreadCents" }
    }

    private val history = DoubleArray(medianWindow)
    private var historySize = 0
    private var historyNext = 0
    private val farRun = DoubleArray(jumpFrames)
    private var farCount = 0
    private var farNext = 0
    private val scratch = DoubleArray(maxOf(medianWindow, jumpFrames))
    private var smoothedCents = Double.NaN
    private var emptyRun = 0

    override fun push(estimate: PitchEstimate?): Double? {
        if (estimate == null || estimate.confidence < minConfidence) {
            emptyRun++
            if (emptyRun >= nullFramesToReset) {
                reset()
                return null
            }
            return currentFrequency()
        }
        emptyRun = 0
        val cents = NoteMath.cents(estimate.frequencyHz, NoteMath.DEFAULT_A4_HZ)

        if (smoothedCents.isNaN()) {
            accept(cents)
            smoothedCents = cents
            return currentFrequency()
        }

        if (abs(cents - smoothedCents) > jumpCents) {
            farRun[farNext] = cents
            farNext = (farNext + 1) % jumpFrames
            if (farCount < jumpFrames) farCount++
            if (farCount < jumpFrames || spread(farRun) > jumpSpreadCents) return currentFrequency()
            val target = median(farRun, jumpFrames)
            farCount = 0
            farNext = 0
            historySize = 0
            historyNext = 0
            accept(target)
            smoothedCents = target
            return currentFrequency()
        }

        farCount = 0
        farNext = 0
        accept(cents)
        val median = median(history, historySize)
        if (abs(median - smoothedCents) > snapCents) {
            smoothedCents = median
        } else {
            smoothedCents += alpha * (median - smoothedCents)
        }
        return currentFrequency()
    }

    override fun reset() {
        historySize = 0
        historyNext = 0
        farCount = 0
        farNext = 0
        smoothedCents = Double.NaN
        emptyRun = 0
    }

    private fun currentFrequency(): Double? =
        if (smoothedCents.isNaN()) null else NoteMath.frequency(NoteMath.A4_MIDI + smoothedCents / 100.0)

    private fun accept(cents: Double) {
        history[historyNext] = cents
        historyNext = (historyNext + 1) % medianWindow
        if (historySize < medianWindow) historySize++
    }

    private fun spread(values: DoubleArray): Double {
        var lo = values[0]
        var hi = values[0]
        for (v in values) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        return hi - lo
    }

    /** Median of the first [count] values of [values]; the mean of the middle pair for even counts. */
    private fun median(values: DoubleArray, count: Int): Double {
        System.arraycopy(values, 0, scratch, 0, count)
        for (i in 1 until count) {
            val v = scratch[i]
            var j = i - 1
            while (j >= 0 && scratch[j] > v) {
                scratch[j + 1] = scratch[j]
                j--
            }
            scratch[j + 1] = v
        }
        val middle = count / 2
        return if (count % 2 == 1) scratch[middle] else 0.5 * (scratch[middle - 1] + scratch[middle])
    }
}
