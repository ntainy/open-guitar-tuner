package dev.ntainy.guitar_tuner.dsp

import kotlin.math.max

/**
 * Detects the start of a new note from the capture level.
 *
 * A peak follower tracks the level and decays at [decayPerChunk] per chunk (never below the current level, so a
 * ringing note is never mistaken for an attack); a chunk that is [ratio] times louder than the decayed peak, and
 * above [minLevel], is a new attack. Re-plucking the same string
 * counts too, which is what the tuner wants: every attack carries a sharp transient that should not be displayed.
 *
 * @param ratio how much louder than the decayed peak a chunk must be (1.8 ≈ +5 dB)
 * @param decayPerChunk peak decay per observed chunk (0.98 ≈ halves in 0.7 s of 21 ms chunks, about as fast as a
 *   plucked string fades, so a re-pluck at the same volume registers within a second)
 * @param minLevel chunks below this RMS never count as an onset
 */
class OnsetDetector(
    private val ratio: Double = 1.8,
    private val decayPerChunk: Double = 0.98,
    private val minLevel: Double = 0.004,
) {
    init {
        require(ratio > 1.0) { "ratio must exceed 1, was $ratio" }
        require(decayPerChunk > 0.0 && decayPerChunk < 1.0) { "decayPerChunk must be in (0, 1), was $decayPerChunk" }
        require(minLevel >= 0.0) { "minLevel must not be negative, was $minLevel" }
    }

    /** Decayed peak level seen so far. */
    var peak: Double = 0.0
        private set

    /** Feed one chunk's RMS; returns true when this chunk starts a new note. */
    fun push(level: Double): Boolean {
        val onset = level >= minLevel && level > peak * ratio
        peak = max(level, peak * decayPerChunk)
        return onset
    }

    fun reset() {
        peak = 0.0
    }
}
