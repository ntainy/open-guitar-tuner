package dev.ntainy.guitar_tuner.audio

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SyntheticToneSourceTest {

    @Test
    fun chunksHaveTheRequestedSizeAndStayInRange() = runTest {
        val source = SyntheticToneSource(chunkSize = 512)
        val chunks = source.samples().take(8).toList()

        assertEquals(8, chunks.size)
        chunks.forEach { chunk ->
            assertEquals(512, chunk.size)
            chunk.forEach { assertTrue(abs(it) <= SyntheticToneSource.PEAK + 1e-3, "sample $it out of range") }
        }
        val peak = chunks.maxOf { chunk -> chunk.maxOf { abs(it) } }
        assertEquals(SyntheticToneSource.PEAK, peak.toDouble(), 0.02)
        assertEquals(SyntheticToneSource.DEVICE, source.device)
        assertEquals(InputKind.TEST_TONE, source.device.kind)
        assertEquals(AudioInputDevice.TEST_TONE_KEY, source.device.key)
        assertEquals(48_000, source.sampleRate)
    }

    @Test
    fun dominantFrequencyMatchesTheRequestedOne() = runTest {
        val source = SyntheticToneSource(MutableStateFlow(110.0))
        val chunks = source.samples().take(6).toList()

        assertEquals(110.0, dominantFrequency(concat(chunks), source.sampleRate), 0.5)
    }

    @Test
    fun frequencyChangesAreHonouredMidStream() = runTest {
        val frequency = MutableStateFlow(82.41)
        val source = SyntheticToneSource(frequency)
        val chunks = source.samples()
            .withIndex()
            .onEach { if (it.index == 5) frequency.value = 329.63 }
            .take(12)
            .toList()
            .map { it.value }

        assertEquals(82.41, dominantFrequency(concat(chunks.subList(0, 6)), source.sampleRate), 0.5)
        assertEquals(329.63, dominantFrequency(concat(chunks.subList(7, 12)), source.sampleRate), 1.0)
    }

    @Test
    fun emissionIsPacedInRealTime() = runTest {
        val source = SyntheticToneSource(sampleRate = 48_000, chunkSize = 1024)
        source.samples().take(10).toList()

        // nine gaps of 1024 / 48000 s = 192 ms of virtual time
        assertTrue(currentTime in 185..200, "10 chunks took $currentTime ms")
    }

    private fun concat(chunks: List<FloatArray>): FloatArray {
        val out = FloatArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { it.copyInto(out, offset); offset += it.size }
        return out
    }

    /**
     * Normalised autocorrelation over 40..1200 Hz; the first peak within 10 % of the global maximum is the
     * period (later peaks at 2T, 3T are just as high, sub-harmonic lags are much lower).
     */
    private fun dominantFrequency(x: FloatArray, sampleRate: Int): Double {
        val minLag = sampleRate / 1200
        val maxLag = sampleRate / 40
        val n = x.size - maxLag - 1
        require(n > maxLag) { "need more samples: ${x.size}" }
        val corr = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag + 1) {
            var dot = 0.0
            var e0 = 0.0
            var e1 = 0.0
            for (i in 0 until n) {
                dot += x[i] * x[i + lag]
                e0 += x[i] * x[i]
                e1 += x[i + lag] * x[i + lag]
            }
            corr[lag] = dot / sqrt(e0 * e1)
        }
        val best = (minLag..maxLag).maxOf { corr[it] }
        val lag = (minLag + 1..maxLag).first { corr[it] >= 0.9 * best && corr[it] >= corr[it - 1] && corr[it] >= corr[it + 1] }
        val a = corr[lag - 1]
        val b = corr[lag]
        val c = corr[lag + 1]
        val denominator = a - 2 * b + c
        val delta = if (denominator == 0.0) 0.0 else 0.5 * (a - c) / denominator
        return sampleRate / (lag + delta)
    }
}
