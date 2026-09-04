package dev.ntainy.guitar_tuner.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic test signals for the DSP chain. Every generator is seeded so failures reproduce.
 */
object SyntheticSignals {
    const val SAMPLE_RATE = DspDefaults.SAMPLE_RATE

    /**
     * A plucked-string-like tone: [harmonics] partials with amplitude `1 / k^1.2`, the second partial raised to
     * [secondHarmonicBoost] times the fundamental (to provoke octave-up errors), inharmonic partial frequencies
     * `f_k = k · f0 · sqrt(1 + B · k²)` with `B = [inharmonicity]`, random partial phases, and white Gaussian noise
     * whose RMS sits [noiseDb] below the fundamental's RMS. The result is normalised to a peak of about 0.5.
     */
    fun pluckedString(
        f0Hz: Double,
        sampleRate: Int = SAMPLE_RATE,
        length: Int = DspDefaults.FRAME_SIZE,
        harmonics: Int = 8,
        secondHarmonicBoost: Double = 1.3,
        inharmonicity: Double = 2e-4,
        noiseDb: Double = -30.0,
        seed: Long = 1L,
    ): FloatArray {
        require(harmonics >= 1) { "need at least the fundamental" }
        val random = Random(seed)
        val out = DoubleArray(length)
        val fundamentalAmplitude = 1.0
        for (k in 1..harmonics) {
            val amplitude =
                if (k == 2) secondHarmonicBoost * fundamentalAmplitude else fundamentalAmplitude / k.toDouble().pow(1.2)
            val frequency = k * f0Hz * sqrt(1.0 + inharmonicity * k * k)
            val phase = random.nextDouble() * 2.0 * PI
            val step = 2.0 * PI * frequency / sampleRate
            for (i in 0 until length) out[i] += amplitude * sin(phase + step * i)
        }
        val noiseRms = 10.0.pow(noiseDb / 20.0) * fundamentalAmplitude / sqrt(2.0)
        for (i in 0 until length) out[i] += noiseRms * random.nextGaussian()
        return normalised(out, 0.5)
    }

    /** Pure sine of [amplitude] peak at [frequencyHz]. */
    fun sine(
        frequencyHz: Double,
        sampleRate: Int = SAMPLE_RATE,
        length: Int = DspDefaults.FRAME_SIZE,
        amplitude: Double = 0.5,
        phase: Double = 0.0,
    ): FloatArray {
        val step = 2.0 * PI * frequencyHz / sampleRate
        return FloatArray(length) { i -> (amplitude * sin(phase + step * i)).toFloat() }
    }

    /** All zeros. */
    fun silence(length: Int = DspDefaults.FRAME_SIZE): FloatArray = FloatArray(length)

    /** White Gaussian noise with the given RMS. */
    fun whiteNoise(length: Int = DspDefaults.FRAME_SIZE, rms: Double = 0.1, seed: Long = 7L): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { (rms * random.nextGaussian()).toFloat() }
    }

    /** `x[i] = i`, useful to check frame boundaries. */
    fun ramp(length: Int): FloatArray = FloatArray(length) { it.toFloat() }

    private fun normalised(samples: DoubleArray, peak: Double): FloatArray {
        var max = 0.0
        for (v in samples) max = maxOf(max, abs(v))
        val scale = if (max > 0.0) peak / max else 1.0
        return FloatArray(samples.size) { (samples[it] * scale).toFloat() }
    }
}
