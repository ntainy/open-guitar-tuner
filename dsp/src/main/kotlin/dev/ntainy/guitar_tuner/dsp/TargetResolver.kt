package dev.ntainy.guitar_tuner.dsp

/**
 * Chooses which string of the tuning a detected pitch belongs to (AUTO mode).
 *
 * Stateful on purpose: once a string is chosen it should only change when another string is clearly closer
 * (see [DspDefaults] and the Wave 1 implementation [HysteresisTargetResolver]) so the display does not flicker
 * between neighbouring strings.
 */
interface TargetResolver {
    /**
     * @param pitchHz detected frequency
     * @param targetsHz frequency of every string, low to high
     * @return index into [targetsHz]
     */
    fun resolve(pitchHz: Double, targetsHz: DoubleArray): Int

    /**
     * Richer form of [resolve]: the string, whether the pitch is one of its overtones ([TargetMatch.fold]), and
     * the cents the string is off once the overtone is folded back. Returns null when the pitch is not plausibly
     * any string of the tuning (far below the lowest or between nothing), which callers should treat as silence.
     *
     * The default delegates to [resolve] with no overtone handling.
     */
    fun resolveMatch(pitchHz: Double, targetsHz: DoubleArray): TargetMatch? {
        val index = resolve(pitchHz, targetsHz)
        return TargetMatch(index, 1, NoteMath.cents(pitchHz, targetsHz[index]))
    }

    fun reset()
}
