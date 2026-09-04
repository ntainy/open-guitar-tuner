package dev.ntainy.guitar_tuner.dsp

import kotlin.math.abs

/**
 * Which string a detected pitch belongs to, allowing for the pitch being an overtone of that string.
 *
 * @property index index into the target list (0 = low string)
 * @property fold 1 when the pitch is the string's fundamental, 2..[Harmonics.MAX_FOLD] when it is that overtone
 * @property centsOff signed offset of the pitch from `fold × target`, i.e. how far the string itself is off
 */
data class TargetMatch(val index: Int, val fold: Int, val centsOff: Double)

/**
 * Overtone folding: a decaying guitar string often hands the detector its 2nd, 3rd or 4th harmonic once the
 * fundamental has faded. Treating 392 Hz on the G string as "G3, an octave up" instead of "E4, 300 cents sharp"
 * keeps the display locked on the string being tuned.
 *
 * A fold above 1 is only considered when the pitch sits within [DEFAULT_MAX_FOLD_CENTS] of the exact multiple,
 * and each fold carries a penalty so that a real string playing that pitch still wins the comparison.
 */
object Harmonics {
    const val MAX_FOLD = 4
    const val DEFAULT_MAX_FOLD_CENTS = 50.0

    /** Score handicap for interpreting a pitch as an overtone; fold 1 is free. */
    fun penaltyCents(fold: Int): Double = when (fold) {
        1 -> 0.0
        2 -> 20.0
        3 -> 45.0
        else -> 60.0
    }

    /** Signed cents of [pitchHz] relative to `fold × targetHz`. */
    fun cents(pitchHz: Double, targetHz: Double, fold: Int): Double = NoteMath.cents(pitchHz, targetHz * fold)

    /** The fold whose `|cents| + penalty` is smallest; folds above 1 must be within [maxFoldCents] of exact. */
    fun bestFold(pitchHz: Double, targetHz: Double, maxFoldCents: Double = DEFAULT_MAX_FOLD_CENTS): Int {
        var best = 1
        var bestScore = abs(cents(pitchHz, targetHz, 1))
        for (fold in 2..MAX_FOLD) {
            val c = abs(cents(pitchHz, targetHz, fold))
            if (c > maxFoldCents) continue
            val score = c + penaltyCents(fold)
            if (score < bestScore) {
                best = fold
                bestScore = score
            }
        }
        return best
    }

    /** `|cents| + penalty` for the best fold of [targetHz]; lower is a better match. */
    fun score(pitchHz: Double, targetHz: Double, fold: Int): Double = abs(cents(pitchHz, targetHz, fold)) + penaltyCents(fold)
}
