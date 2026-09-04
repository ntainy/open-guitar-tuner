package dev.ntainy.guitar_tuner.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YinPitchDetectorTest {
    private val detector = YinPitchDetector()
    private val offsets = doubleArrayOf(-40.0, -10.0, 0.0, 10.0, 40.0)
    private val midiRange = 35..67

    private fun detectPlucked(midi: Int, offsetCents: Double, inharmonicity: Double): Pair<Double, PitchEstimate?> {
        val f0 = NoteMath.frequency(midi + offsetCents / 100.0)
        val frame = SyntheticSignals.pluckedString(
            f0,
            inharmonicity = inharmonicity,
            seed = midi * 100L + offsetCents.toLong(),
        )
        return f0 to detector.detect(frame)
    }

    @Test
    fun pluckedStringsAcrossTheFretboardAreWithinOneCent() {
        // B = 5e-5 is a typical plain-string inharmonicity. Larger B shifts the waveform period itself; see below.
        for (midi in midiRange) {
            for (offset in offsets) {
                val (f0, estimate) = detectPlucked(midi, offset, inharmonicity = TYPICAL_INHARMONICITY)
                assertNotNull(estimate, "${NoteMath.name(midi)} $offset cents")
                val error = NoteMath.cents(estimate.frequencyHz, f0)
                assertTrue(abs(error) < 1.0, "${NoteMath.name(midi)} $offset cents: error $error cents")
            }
        }
    }

    @Test
    fun harmonicPluckedStringsAreWithinATenthOfACent() {
        for (midi in midiRange) {
            for (offset in offsets) {
                val (f0, estimate) = detectPlucked(midi, offset, inharmonicity = 0.0)
                assertNotNull(estimate, "${NoteMath.name(midi)} $offset cents")
                val error = NoteMath.cents(estimate.frequencyHz, f0)
                assertTrue(abs(error) < 0.15, "${NoteMath.name(midi)} $offset cents: error $error cents")
            }
        }
    }

    @Test
    fun stronglyInharmonicStringsReadTheirPhysicalPeriodConsistently() {
        // With B = 2e-4 the partial sum is not periodic at 1/f0; its best-fit period is about 0.11 % shorter,
        // so a period detector reads roughly +2 cents. The spread around that shift is the detector's own error.
        var minError = Double.MAX_VALUE
        var maxError = -Double.MAX_VALUE
        for (midi in midiRange) {
            for (offset in offsets) {
                val (f0, estimate) = detectPlucked(midi, offset, inharmonicity = STRONG_INHARMONICITY)
                assertNotNull(estimate, "${NoteMath.name(midi)} $offset cents")
                val error = NoteMath.cents(estimate.frequencyHz, f0)
                minError = minOf(minError, error)
                maxError = maxOf(maxError, error)
            }
        }
        assertTrue(
            minError > 1.5 && maxError < 2.5,
            "expected a +1.5..+2.5 cent period shift, got $minError..$maxError",
        )
        assertTrue(maxError - minError < 0.5, "detector spread ${maxError - minError} cents should be sub-cent")
    }

    @Test
    fun printsAccuracyTable() {
        println("YIN accuracy, plucked string, offsets -40/-10/0/+10/+40 cents (error in cents vs true f0)")
        println("inharmonicity B | mean    | min     | max     | max |err| | min confidence")
        for (b in doubleArrayOf(0.0, TYPICAL_INHARMONICITY, 1e-4, STRONG_INHARMONICITY)) {
            var sum = 0.0
            var min = Double.MAX_VALUE
            var max = -Double.MAX_VALUE
            var maxAbs = 0.0
            var minConfidence = 1.0
            var count = 0
            for (midi in midiRange) {
                for (offset in offsets) {
                    val (f0, estimate) = detectPlucked(midi, offset, inharmonicity = b)
                    val error = NoteMath.cents(assertNotNull(estimate).frequencyHz, f0)
                    sum += error
                    min = minOf(min, error)
                    max = maxOf(max, error)
                    maxAbs = maxOf(maxAbs, abs(error))
                    minConfidence = minOf(minConfidence, estimate.confidence)
                    count++
                }
            }
            println(
                "%-15s | %+7.3f | %+7.3f | %+7.3f | %9.3f | %.3f".format(
                    "%.0e".format(b), sum / count, min, max, maxAbs, minConfidence,
                ),
            )
        }
        println("Standard strings, B = %.0e, per offset (error in cents):".format(TYPICAL_INHARMONICITY))
        println("string | " + offsets.joinToString(" | ") { "%+5.0f c".format(it) })
        for (midi in intArrayOf(40, 45, 50, 55, 59, 64)) {
            val row = offsets.joinToString(" | ") { offset ->
                val (f0, estimate) = detectPlucked(midi, offset, inharmonicity = TYPICAL_INHARMONICITY)
                "%+7.3f".format(NoteMath.cents(assertNotNull(estimate).frequencyHz, f0))
            }
            println("%-6s | %s".format(NoteMath.name(midi), row))
        }
    }

    @Test
    fun pureSinesAreWithinHalfACent() {
        for (hz in doubleArrayOf(82.41, 329.63)) {
            val estimate = detector.detect(SyntheticSignals.sine(hz))
            assertNotNull(estimate, "$hz Hz")
            val error = NoteMath.cents(estimate.frequencyHz, hz)
            assertTrue(abs(error) < 0.5, "$hz Hz: error $error cents")
        }
    }

    @Test
    fun silenceIsNull() {
        assertNull(detector.detect(SyntheticSignals.silence()))
    }

    @Test
    fun toneBelowTheLevelGateIsNull() {
        assertNull(detector.detect(SyntheticSignals.sine(110.0, amplitude = 0.002)))
        assertNotNull(detector.detect(SyntheticSignals.sine(110.0, amplitude = 0.01)))
    }

    @Test
    fun whiteNoiseIsNull() {
        for (seed in 1L..5L) {
            assertNull(detector.detect(SyntheticSignals.whiteNoise(rms = 0.2, seed = seed)), "seed $seed")
        }
    }

    @Test
    fun dcOffsetIsIgnored() {
        val silentWithDc = FloatArray(DspDefaults.FRAME_SIZE) { 0.4f }
        assertNull(detector.detect(silentWithDc), "a constant offset is not a signal")

        val tone = SyntheticSignals.sine(196.0, amplitude = 0.2)
        for (i in tone.indices) tone[i] += 0.5f
        val estimate = detector.detect(tone)
        assertNotNull(estimate)
        assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, 196.0), 0.5)
        assertEquals(0.2 / kotlin.math.sqrt(2.0), estimate.rms, 0.002, "rms excludes the DC component")
    }

    @Test
    fun lowToneAt45HzIsDetected() {
        val estimate = detector.detect(SyntheticSignals.sine(45.0))
        assertNotNull(estimate)
        assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, 45.0), 1.0)
    }

    @Test
    fun confidenceForCleanTonesIsHigh() {
        for (hz in doubleArrayOf(45.0, 82.41, 110.0, 196.0, 329.63, 392.0)) {
            val sine = detector.detect(SyntheticSignals.sine(hz))
            assertNotNull(sine)
            assertTrue(sine.confidence > 0.8, "sine $hz Hz confidence ${sine.confidence}")
            val plucked = detector.detect(SyntheticSignals.pluckedString(hz))
            assertNotNull(plucked)
            assertTrue(plucked.confidence > 0.8, "plucked $hz Hz confidence ${plucked.confidence}")
        }
    }

    @Test
    fun dominantSecondHarmonicDoesNotCauseAnOctaveError() {
        for (hz in doubleArrayOf(82.41, 146.83, 246.94)) {
            val frame = SyntheticSignals.pluckedString(hz, secondHarmonicBoost = 3.0, inharmonicity = 0.0)
            val estimate = detector.detect(frame)
            assertNotNull(estimate, "$hz Hz")
            assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, hz), 1.0, "$hz Hz")
        }
    }

    @Test
    fun rmsReportsTheFrameLevel() {
        val estimate = detector.detect(SyntheticSignals.sine(220.0, amplitude = 0.5))
        assertNotNull(estimate)
        assertEquals(0.5 / kotlin.math.sqrt(2.0), estimate.rms, 0.002)
    }

    @Test
    fun lagRangeFollowsTheFrequencyRange() {
        assertEquals(40, detector.minLag)
        assertEquals(1200, detector.maxLag)
        assertEquals(DspDefaults.FRAME_SIZE - 1200, detector.windowSize)
    }

    @Test
    fun rejectsShortFramesAndBadGeometry() {
        assertFailsWith<IllegalArgumentException> { detector.detect(FloatArray(DspDefaults.FRAME_SIZE - 1)) }
        assertFailsWith<IllegalArgumentException> { YinPitchDetector(frameSize = 2048, minFrequencyHz = 40.0) }
        assertFailsWith<IllegalArgumentException> { YinPitchDetector(minFrequencyHz = 500.0, maxFrequencyHz = 100.0) }
    }

    @Test
    fun acceptsFramesLongerThanFrameSize() {
        val estimate = detector.detect(SyntheticSignals.sine(110.0, length = DspDefaults.FRAME_SIZE + 500))
        assertNotNull(estimate)
        assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, 110.0), 0.5)
    }

    private companion object {
        const val TYPICAL_INHARMONICITY = 5e-5
        const val STRONG_INHARMONICITY = 2e-4
    }
}
