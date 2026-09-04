package dev.ntainy.guitar_tuner.dsp

import kotlin.math.abs

/**
 * [TargetResolver] with overtone folding and hysteresis so the chosen string does not flicker.
 *
 * Every string is scored as `|cents| + penalty` for its best [Harmonics] fold (fundamental or an overtone within
 * [maxFoldCents]). The first call picks the best score (lowest index on a tie, so duplicated notes such as the two
 * D3 strings of "Double Daddy" resolve deterministically). Afterwards the current string is kept unless another
 * string scores better by more than [switchMarginCents] on [framesToSwitch] consecutive calls. Hysteresis is
 * meant for a ringing note; the engine calls [reset] on every new attack so a fresh pluck picks its string at
 * once. A pitch whose best
 * score exceeds [implausibleCents] (for example a 45 Hz hum against a low E of 82 Hz) is reported as null and
 * leaves the state untouched. [reset] forgets the current string; call it when the tuning changes.
 *
 * @param switchMarginCents how much better another string must score before it is considered
 * @param framesToSwitch consecutive calls the margin must hold before the switch happens
 * @param maxFoldCents how close to an exact multiple a pitch must be to count as an overtone
 * @param implausibleCents best score beyond which the pitch is not any string
 */
class HysteresisTargetResolver(
    private val switchMarginCents: Double = 30.0,
    private val framesToSwitch: Int = 3,
    private val maxFoldCents: Double = Harmonics.DEFAULT_MAX_FOLD_CENTS,
    private val implausibleCents: Double = 600.0,
) : TargetResolver {
    init {
        require(switchMarginCents >= 0.0) { "switchMarginCents must not be negative, was $switchMarginCents" }
        require(framesToSwitch >= 1) { "framesToSwitch must be positive, was $framesToSwitch" }
        require(maxFoldCents >= 0.0) { "maxFoldCents must not be negative, was $maxFoldCents" }
        require(implausibleCents > 0.0) { "implausibleCents must be positive, was $implausibleCents" }
    }

    private var current = NONE
    private var candidate = NONE
    private var candidateRun = 0
    private var folds = IntArray(8)
    private var cents = DoubleArray(8)
    private var scores = DoubleArray(8)

    override fun resolve(pitchHz: Double, targetsHz: DoubleArray): Int {
        val match = resolveMatch(pitchHz, targetsHz)
        if (match != null) return match.index
        if (current != NONE && current < targetsHz.size) return current
        return targetsHz.indices.minByOrNull { abs(NoteMath.cents(pitchHz, targetsHz[it])) } ?: 0
    }

    override fun resolveMatch(pitchHz: Double, targetsHz: DoubleArray): TargetMatch? {
        require(targetsHz.isNotEmpty()) { "targetsHz must not be empty" }
        require(pitchHz > 0.0) { "pitchHz must be positive, was $pitchHz" }
        ensureScratch(targetsHz.size)

        var best = 0
        var bestScore = Double.MAX_VALUE
        for (i in targetsHz.indices) {
            val fold = Harmonics.bestFold(pitchHz, targetsHz[i], maxFoldCents)
            val c = Harmonics.cents(pitchHz, targetsHz[i], fold)
            val score = abs(c) + Harmonics.penaltyCents(fold)
            folds[i] = fold
            cents[i] = c
            scores[i] = score
            if (score < bestScore) {
                best = i
                bestScore = score
            }
        }

        if (bestScore > implausibleCents) {
            candidate = NONE
            candidateRun = 0
            return null
        }

        if (current == NONE || current >= targetsHz.size) {
            current = best
            candidate = NONE
            candidateRun = 0
        } else if (best != current && scores[current] - bestScore > switchMarginCents) {
            if (candidate == best) candidateRun++ else {
                candidate = best
                candidateRun = 1
            }
            if (candidateRun >= framesToSwitch) {
                current = best
                candidate = NONE
                candidateRun = 0
            }
        } else {
            candidate = NONE
            candidateRun = 0
        }
        return TargetMatch(current, folds[current], cents[current])
    }

    override fun reset() {
        current = NONE
        candidate = NONE
        candidateRun = 0
    }

    private fun ensureScratch(size: Int) {
        if (folds.size < size) {
            folds = IntArray(size)
            cents = DoubleArray(size)
            scores = DoubleArray(size)
        }
    }

    private companion object {
        const val NONE = -1
    }
}
