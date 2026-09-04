package dev.ntainy.guitar_tuner.dsp

import kotlin.math.abs

/**
 * Fixes octave errors by context: while a note rings, a reading that sits within [maxCents] of one, two octaves
 * below or above the last accepted pitch is folded back onto it.
 *
 * YIN on a decaying guitar string occasionally locks onto twice or four times the period (54 Hz for an A2 at
 * 109 Hz) with high confidence; nothing about the frame itself gives that away, but a string cannot drop an
 * octave without a new attack. Call [reset] on every onset so a genuinely new note (which may well be an octave
 * of the old one) is accepted as it is. Readings far from every octave of the reference move the reference.
 *
 * @param maxCents how close to an exact octave multiple a reading must be to be folded
 */
class OctaveGuard(private val maxCents: Double = 40.0) {
    init {
        require(maxCents >= 0.0) { "maxCents must not be negative, was $maxCents" }
    }

    /** Last accepted (possibly folded) frequency, or NaN before the first reading since [reset]. */
    var referenceHz: Double = Double.NaN
        private set

    fun apply(estimate: PitchEstimate?): PitchEstimate? {
        if (estimate == null) return null
        val hz = estimate.frequencyHz
        if (referenceHz.isNaN()) {
            referenceHz = hz
            return estimate
        }
        var fixed: PitchEstimate = estimate
        for (factor in FACTORS) {
            val candidate = hz * factor
            if (abs(NoteMath.cents(candidate, referenceHz)) <= maxCents) {
                fixed = estimate.copy(frequencyHz = candidate)
                break
            }
        }
        referenceHz = fixed.frequencyHz
        return fixed
    }

    fun reset() {
        referenceHz = Double.NaN
    }

    private companion object {
        /** Unity first so an honest reading is never touched; then one and two octaves each way. */
        val FACTORS = doubleArrayOf(1.0, 2.0, 0.5, 4.0, 0.25)
    }
}
