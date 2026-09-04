package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Times [YinPitchDetector.detect] on 4096-sample frames after a JIT warm-up. The bound is loose to avoid
 * flakiness on a busy machine; the printed averages are what matter for the DSP report.
 */
class PerformanceTest {
    private val detector = YinPitchDetector()

    /** Prevents the JIT from discarding the detections as dead code. */
    private var sink = 0.0

    @Test
    fun detectAveragesUnderFiveMilliseconds() {
        val typical = SyntheticSignals.pluckedString(110.0)
        val high = SyntheticSignals.pluckedString(329.63)
        val low = SyntheticSignals.pluckedString(45.0)
        val noise = SyntheticSignals.whiteNoise()
        repeat(WARM_UP) {
            sink += detector.detect(typical)?.frequencyHz ?: 0.0
            sink += detector.detect(high)?.frequencyHz ?: 0.0
            sink += detector.detect(low)?.frequencyHz ?: 0.0
            sink += detector.detect(noise)?.frequencyHz ?: 0.0
        }

        val typicalMs = averageMillis(typical)
        val highMs = averageMillis(high)
        val lowMs = averageMillis(low)
        val noiseMs = averageMillis(noise)
        println(
            "YIN detect, %d-sample frame, %d timed calls each: ".format(DspDefaults.FRAME_SIZE, TIMED) +
                "A2 110 Hz %.3f ms, E4 330 Hz %.3f ms, 45 Hz %.3f ms, white noise (full lag scan) %.3f ms".format(
                    typicalMs, highMs, lowMs, noiseMs,
                ),
        )
        assertTrue(typicalMs < 5.0, "typical frame took $typicalMs ms")
        assertTrue(noiseMs < 5.0, "worst case (full scan) took $noiseMs ms")
    }

    private fun averageMillis(frame: FloatArray): Double {
        val start = System.nanoTime()
        repeat(TIMED) { sink += detector.detect(frame)?.frequencyHz ?: 0.0 }
        return (System.nanoTime() - start) / 1e6 / TIMED
    }

    private companion object {
        const val WARM_UP = 300
        const val TIMED = 100
    }
}
