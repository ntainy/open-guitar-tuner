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

    fun reset()
}
