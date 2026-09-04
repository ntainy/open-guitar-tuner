package dev.ntainy.guitar_tuner.dsp

import kotlin.math.abs

/**
 * [TargetResolver] with hysteresis so the chosen string does not flicker between neighbours.
 *
 * The first call picks the target nearest to the pitch in cents (lowest index on a tie, so duplicated notes such
 * as the two D3 strings of "Double Daddy" resolve deterministically). Afterwards the current target is kept unless
 * another target is closer by more than [switchMarginCents] on [framesToSwitch] consecutive calls, in which case
 * the resolver switches to it. A call that does not meet the margin, or that favours a different candidate,
 * restarts the count. [reset] forgets the current target; call it when the tuning changes.
 *
 * @param switchMarginCents how much closer (in cents) another target must be before it is considered
 * @param framesToSwitch consecutive calls the margin must hold before the switch happens
 */
class HysteresisTargetResolver(
    private val switchMarginCents: Double = 30.0,
    private val framesToSwitch: Int = 3,
) : TargetResolver {
    init {
        require(switchMarginCents >= 0.0) { "switchMarginCents must not be negative, was $switchMarginCents" }
        require(framesToSwitch >= 1) { "framesToSwitch must be positive, was $framesToSwitch" }
    }

    private var current = NONE
    private var candidate = NONE
    private var candidateRun = 0

    override fun resolve(pitchHz: Double, targetsHz: DoubleArray): Int {
        require(targetsHz.isNotEmpty()) { "targetsHz must not be empty" }
        require(pitchHz > 0.0) { "pitchHz must be positive, was $pitchHz" }

        var nearest = 0
        var nearestDistance = distance(pitchHz, targetsHz[0])
        for (i in 1 until targetsHz.size) {
            val d = distance(pitchHz, targetsHz[i])
            if (d < nearestDistance) {
                nearest = i
                nearestDistance = d
            }
        }

        if (current == NONE || current >= targetsHz.size) {
            current = nearest
            candidate = NONE
            candidateRun = 0
            return current
        }
        if (nearest == current) {
            candidate = NONE
            candidateRun = 0
            return current
        }

        val currentDistance = distance(pitchHz, targetsHz[current])
        if (currentDistance - nearestDistance > switchMarginCents) {
            if (candidate == nearest) candidateRun++ else {
                candidate = nearest
                candidateRun = 1
            }
            if (candidateRun >= framesToSwitch) {
                current = nearest
                candidate = NONE
                candidateRun = 0
            }
        } else {
            candidate = NONE
            candidateRun = 0
        }
        return current
    }

    override fun reset() {
        current = NONE
        candidate = NONE
        candidateRun = 0
    }

    private fun distance(pitchHz: Double, targetHz: Double): Double = abs(NoteMath.cents(pitchHz, targetHz))

    private companion object {
        const val NONE = -1
    }
}
