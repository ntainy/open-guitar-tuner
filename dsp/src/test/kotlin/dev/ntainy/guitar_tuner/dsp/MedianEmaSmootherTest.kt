package dev.ntainy.guitar_tuner.dsp

import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MedianEmaSmootherTest {
    private fun estimate(hz: Double, confidence: Double = 0.95) = PitchEstimate(hz, confidence, rms = 0.1)

    private fun centsBetween(a: Double, b: Double) = NoteMath.cents(a, b)

    @Test
    fun constantInputGivesTheSameOutput() {
        val smoother = MedianEmaSmoother()
        repeat(20) {
            val out = smoother.push(estimate(110.0))
            assertNotNull(out)
            assertEquals(110.0, out, 1e-9)
        }
    }

    @Test
    fun firstEstimateIsReportedImmediately() {
        val smoother = MedianEmaSmoother()
        assertEquals(196.0, smoother.push(estimate(196.0)) ?: 0.0, 1e-9)
    }

    @Test
    fun singleOctaveOutlierIsIgnored() {
        val smoother = MedianEmaSmoother()
        repeat(5) { smoother.push(estimate(82.41)) }
        val duringOutlier = smoother.push(estimate(164.82))
        assertNotNull(duringOutlier)
        assertEquals(0.0, centsBetween(duringOutlier, 82.41), 0.01)
        val after = smoother.push(estimate(82.41))
        assertNotNull(after)
        assertEquals(0.0, centsBetween(after, 82.41), 0.01)
    }

    @Test
    fun twoConsecutiveOutliersAreStillIgnored() {
        val smoother = MedianEmaSmoother(jumpFrames = 3)
        repeat(5) { smoother.push(estimate(110.0)) }
        smoother.push(estimate(220.0))
        val second = smoother.push(estimate(220.0))
        assertNotNull(second)
        assertEquals(0.0, centsBetween(second, 110.0), 0.01)
    }

    @Test
    fun persistentChangeIsFollowedWithinAFewFrames() {
        val smoother = MedianEmaSmoother(jumpFrames = 3)
        repeat(5) { smoother.push(estimate(110.0)) }
        var framesUntilFollowed = -1
        for (frame in 1..6) {
            val out = smoother.push(estimate(146.83))
            assertNotNull(out)
            if (abs(centsBetween(out, 146.83)) < 1.0) {
                framesUntilFollowed = frame
                break
            }
        }
        assertEquals(3, framesUntilFollowed, "should jump on the third consecutive far frame")
    }

    @Test
    fun smallDriftIsTrackedSmoothly() {
        val smoother = MedianEmaSmoother(alpha = 0.45)
        repeat(5) { smoother.push(estimate(110.0)) }
        val shifted = NoteMath.frequency(NoteMath.midiFromFrequency(110.0) + 0.2)
        var lastCents = 0.0
        for (frame in 1..12) {
            val out = smoother.push(estimate(shifted))
            assertNotNull(out)
            val cents = centsBetween(out, 110.0)
            assertTrue(cents >= lastCents - 1e-9, "must move monotonically towards the new value")
            assertTrue(cents <= 20.0 + 1e-9)
            lastCents = cents
        }
        assertTrue(lastCents > 19.0, "after 12 frames the EMA should be within a cent, was $lastCents")
    }

    @Test
    fun jitterIsReduced() {
        val smoother = MedianEmaSmoother()
        val random = Random(42)
        val inputCents = mutableListOf<Double>()
        val outputCents = mutableListOf<Double>()
        repeat(400) {
            val cents = 6.0 * random.nextGaussian()
            val hz = NoteMath.frequency(NoteMath.midiFromFrequency(196.0) + cents / 100.0)
            val out = smoother.push(estimate(hz))
            assertNotNull(out)
            if (it >= 20) {
                inputCents += cents
                outputCents += centsBetween(out, 196.0)
            }
        }
        val inputStd = standardDeviation(inputCents)
        val outputStd = standardDeviation(outputCents)
        assertTrue(outputStd < 0.6 * inputStd, "output std $outputStd should be well below input std $inputStd")
    }

    @Test
    fun outputIsHeldThroughShortGapsAndNullAfterALongOne() {
        val smoother = MedianEmaSmoother(nullFramesToReset = 4)
        repeat(5) { smoother.push(estimate(246.94)) }
        repeat(3) {
            val held = smoother.push(null)
            assertNotNull(held, "gap frame ${it + 1} should hold the last value")
            assertEquals(0.0, centsBetween(held, 246.94), 0.01)
        }
        assertNull(smoother.push(null), "fourth empty frame must clear the output")
        val fresh = smoother.push(estimate(329.63))
        assertNotNull(fresh)
        assertEquals(0.0, centsBetween(fresh, 329.63), 0.01, "after the reset the next value is adopted immediately")
    }

    @Test
    fun lowConfidenceCountsAsEmpty() {
        val smoother = MedianEmaSmoother(minConfidence = 0.5, nullFramesToReset = 4)
        assertNull(smoother.push(estimate(110.0, confidence = 0.2)))
        repeat(3) { smoother.push(estimate(110.0)) }
        repeat(4) { smoother.push(estimate(880.0, confidence = 0.3)) }
        assertNull(smoother.push(null))
    }

    @Test
    fun resetForgetsEverything() {
        val smoother = MedianEmaSmoother()
        repeat(5) { smoother.push(estimate(110.0)) }
        smoother.push(estimate(220.0))
        smoother.reset()
        val out = smoother.push(estimate(220.0))
        assertNotNull(out)
        assertEquals(0.0, centsBetween(out, 220.0), 0.01, "after reset the first value is adopted as is")
    }

    @Test
    fun alphaOfOneFollowsTheMedianExactly() {
        val smoother = MedianEmaSmoother(alpha = 1.0, medianWindow = 1)
        smoother.push(estimate(100.0))
        val out = smoother.push(estimate(101.0))
        assertNotNull(out)
        assertEquals(101.0, out, 1e-9)
    }

    private fun standardDeviation(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}

class MedianEmaSmootherSnapTest {
    private fun estimate(hz: Double) = PitchEstimate(hz, 0.95, rms = 0.1)

    @Test
    fun aStringChangeNeverShowsAnIntermediatePitch() {
        val s = MedianEmaSmoother()
        repeat(5) { s.push(estimate(196.0)) }
        // scattered attack transients (as logged on a real pluck), then the settled B string
        val outputs = listOf(215.4, 227.9, 61.3, 244.4, 245.0, 245.3, 245.1).map { s.push(estimate(it))!! }
        outputs.forEach { hz ->
            kotlin.test.assertTrue(hz < 197.0 || hz > 240.0, "intermediate pitch shown: $hz")
        }
        kotlin.test.assertEquals(245.1, outputs.last(), 1.0)
    }

    @Test
    fun aSmallStepStillEasesIn() {
        val s = MedianEmaSmoother(alpha = 0.45, snapCents = 25.0)
        repeat(5) { s.push(estimate(100.0)) }
        val stepped = NoteMath.frequency(NoteMath.midiFromFrequency(100.0) + 0.10) // +10 cents
        repeat(3) { s.push(estimate(stepped)) }
        val out = s.push(estimate(stepped))!!
        val cents = NoteMath.cents(out, 100.0)
        kotlin.test.assertTrue(cents > 5.0 && cents < 10.0, "expected easing between 5 and 10 cents, was $cents")
    }
}
