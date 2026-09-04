package dev.ntainy.guitar_tuner.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Off-line synthesis of one plucked-string note, used by the reference-tone player on the Tune screen.
 *
 * The model is the additive one the debug `SyntheticToneSource` uses, plus an envelope so it reads as a note
 * rather than a beep:
 *
 * - **Partials.** [HARMONICS] sine partials at exact integer multiples of the fundamental (no inharmonicity: this
 *   is a reference pitch, so the overtones must not pull the ear off the note). Partial `k` starts at
 *   `1 / k^`[BRIGHTNESS], so the fundamental dominates and a tuner analysing the tone cannot mistake an overtone
 *   for the pitch. Partials at or above Nyquist are dropped, so any frequency is safe to ask for.
 * - **Damping.** Partial `k` decays with time constant [DECAY_SECONDS]` / k^`[DAMPING] — the highs die first, which
 *   is what makes a real string sound plucked: bright at the attack, close to a pure fundamental in the tail.
 * - **Attack and release.** A raised-cosine fade in over [ATTACK_SECONDS] (short enough to still read as a pluck,
 *   long enough that the buffer does not start with a click) and a raised-cosine fade out over [RELEASE_SECONDS],
 *   so the first and last samples are exactly zero and the buffer never ends mid-cycle.
 * - **Level.** The whole buffer is scaled so its true peak is exactly [PEAK]; nothing can clip whatever the pitch.
 *
 * Pure Kotlin and allocation-cheap (one pass per partial, three short passes for the envelope); a 1.4 s note at
 * 48 kHz is ~67 k samples and takes a couple of milliseconds to render.
 */
object PluckedToneSynth {

    /** Note length in seconds: long enough to tune a string against, short enough not to outstay a long-press. */
    const val DEFAULT_DURATION_SECONDS = 1.4

    /** Peak absolute sample value of the rendered note. Below full scale, so mixing headroom is never an issue. */
    const val PEAK = 0.85

    /** Fundamental plus six overtones, matching `SyntheticToneSource`. */
    const val HARMONICS = 7

    /** Amplitude of partial `k` before the envelope: `k^-BRIGHTNESS`. Higher = darker tone. */
    const val BRIGHTNESS = 1.3

    /** Decay time constant of the fundamental, in seconds. */
    const val DECAY_SECONDS = 0.40

    /** Partial `k` decays `k^DAMPING` times faster than the fundamental. */
    const val DAMPING = 0.55

    /** Fade-in length; removes the click a hard buffer start would make. */
    const val ATTACK_SECONDS = 0.006

    /** Fade-out length; guarantees the buffer ends at silence instead of mid-cycle. */
    const val RELEASE_SECONDS = 0.060

    /**
     * Renders one note at [frequencyHz].
     *
     * @param sampleRate output rate in Hz
     * @param durationSeconds total length of the returned buffer, envelope included
     * @return mono samples in `[-`[PEAK]`, `[PEAK]`]`, starting and ending at exactly 0
     */
    fun render(
        frequencyHz: Double,
        sampleRate: Int = DspDefaults.SAMPLE_RATE,
        durationSeconds: Double = DEFAULT_DURATION_SECONDS,
    ): FloatArray {
        require(frequencyHz > 0.0) { "frequencyHz must be positive, was $frequencyHz" }
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(durationSeconds > 0.0) { "durationSeconds must be positive, was $durationSeconds" }

        val count = (durationSeconds * sampleRate).toInt().coerceAtLeast(MIN_SAMPLES)
        val nyquist = sampleRate / 2.0
        val out = DoubleArray(count)

        for (k in 1..HARMONICS) {
            val partialHz = k * frequencyHz
            if (partialHz >= nyquist) break
            val step = TWO_PI * partialHz / sampleRate
            val decaySeconds = DECAY_SECONDS / k.toDouble().pow(DAMPING)
            // One exp per partial instead of one per sample: the envelope is a geometric sequence in time.
            val perSample = exp(-1.0 / (decaySeconds * sampleRate))
            var amplitude = 1.0 / k.toDouble().pow(BRIGHTNESS)
            for (i in 0 until count) {
                out[i] += amplitude * sin(step * i)
                amplitude *= perSample
            }
        }

        val attack = (ATTACK_SECONDS * sampleRate).toInt().coerceIn(1, count / 2)
        for (i in 0 until attack) out[i] *= fade(i.toDouble() / attack)
        val release = (RELEASE_SECONDS * sampleRate).toInt().coerceIn(1, count / 2)
        for (i in 0 until release) out[count - 1 - i] *= fade(i.toDouble() / release)

        var max = 0.0
        for (v in out) max = maxOf(max, abs(v))
        val gain = if (max > 0.0) PEAK / max else 0.0
        return FloatArray(count) { (out[it] * gain).toFloat() }
    }

    /** Raised-cosine fade: 0 at `x = 0`, 1 at `x = 1`, flat slope at both ends. */
    private fun fade(x: Double): Double = 0.5 * (1.0 - cos(PI * x))

    private const val TWO_PI = 2.0 * PI
    private const val MIN_SAMPLES = 2
}
