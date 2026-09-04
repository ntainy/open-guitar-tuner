package dev.ntainy.guitar_tuner.audio

import dev.ntainy.guitar_tuner.dsp.DspDefaults
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

/**
 * Debug-build [AudioSource] that synthesises a plucked-string-like tone: the fundamental plus six harmonics
 * with 1/k amplitude, normalised to a peak of [PEAK]. Emission is paced in real time (one chunk every
 * `chunkSize / sampleRate` seconds) so the rest of the chain behaves as it does with a microphone.
 *
 * [frequencyHz] can be changed at any time (a Settings slider, a test); the next chunk picks it up with
 * continuous phase.
 */
class SyntheticToneSource(
    val frequencyHz: MutableStateFlow<Double> = MutableStateFlow(DEFAULT_FREQUENCY_HZ),
    override val sampleRate: Int = DspDefaults.SAMPLE_RATE,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) : AudioSource {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(chunkSize > 0) { "chunkSize must be positive, was $chunkSize" }
    }

    override val device: AudioInputDevice = DEVICE

    override fun samples(): Flow<FloatArray> = flow {
        var phase = 0.0
        var carryNanos = 0L
        val chunkNanos = chunkSize * NANOS_PER_SECOND / sampleRate
        while (true) {
            val step = TWO_PI * frequencyHz.value / sampleRate
            val chunk = FloatArray(chunkSize)
            for (i in chunk.indices) {
                chunk[i] = (waveform(phase) * GAIN).toFloat()
                phase += step
                if (phase >= TWO_PI) phase -= TWO_PI
            }
            emit(chunk)
            val waitNanos = chunkNanos + carryNanos
            val waitMillis = waitNanos / NANOS_PER_MILLI
            carryNanos = waitNanos - waitMillis * NANOS_PER_MILLI
            if (waitMillis > 0) delay(waitMillis) else yield()
        }
    }

    companion object {
        const val DEFAULT_FREQUENCY_HZ = 110.0
        const val DEFAULT_CHUNK_SIZE = 1024

        /** Largest absolute sample value the tone reaches. */
        const val PEAK = 0.3

        /** Fundamental plus six overtones. */
        const val HARMONICS = 7

        /** The device the test tone shows up as in the input list. */
        val DEVICE = AudioInputDevice(
            id = -1,
            key = AudioInputDevice.TEST_TONE_KEY,
            name = "Test tone (debug)",
            kind = InputKind.TEST_TONE,
        )

        private const val TWO_PI = 2.0 * PI
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** Unnormalised waveform at [phase] radians of the fundamental. */
        private fun waveform(phase: Double): Double {
            var sum = 0.0
            for (k in 1..HARMONICS) sum += sin(k * phase) / k
            return sum
        }

        /** Scales the waveform so its true peak over one period is exactly [PEAK]. */
        private val GAIN: Double = PEAK / (0 until 4096).maxOf { abs(waveform(TWO_PI * it / 4096)) }
    }
}
