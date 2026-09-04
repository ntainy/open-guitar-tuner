package dev.ntainy.guitar_tuner.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves the reference tone is the note it claims to be: the project's own YIN detector has to read the requested
 * pitch back out of the rendered buffer, early in the note and deep into its decay.
 */
class PluckedToneSynthTest {

    private val sampleRate = DspDefaults.SAMPLE_RATE

    /** Standard tuning, low to high. */
    private val standardMidi = listOf(40, 45, 50, 55, 59, 64)

    @Test
    fun yinReadsBackTheRequestedPitchThroughTheWholeNote() {
        val detector = YinPitchDetector()
        for (midi in standardMidi) {
            val target = NoteMath.frequency(midi)
            val note = PluckedToneSynth.render(target, sampleRate)
            for (seconds in listOf(0.05, 0.30, 0.90)) {
                val frame = window(note, (seconds * sampleRate).toInt(), detector.frameSize)
                val estimate = detector.detect(frame)
                    ?: error("${NoteMath.name(midi)} at $seconds s was read as silence")
                val cents = NoteMath.cents(estimate.frequencyHz, target)
                assertTrue(
                    abs(cents) < 1.0,
                    "${NoteMath.name(midi)} ($target Hz) at $seconds s read as ${estimate.frequencyHz} Hz " +
                        "($cents cents off)",
                )
                assertTrue(estimate.confidence > 0.9, "confidence ${estimate.confidence} at $seconds s")
            }
        }
    }

    @Test
    fun nothingClipsAndThePeakIsExact() {
        for (midi in standardMidi) {
            val note = PluckedToneSynth.render(NoteMath.frequency(midi), sampleRate)
            val peak = note.maxOf { abs(it) }.toDouble()
            assertTrue(peak <= 1.0, "sample peak $peak reached full scale")
            assertEquals(PluckedToneSynth.PEAK, peak, 1e-4, "peak of ${NoteMath.name(midi)}")
        }
    }

    @Test
    fun theBufferStartsAndEndsAtSilenceSoThereIsNoClick() {
        val note = PluckedToneSynth.render(110.0, sampleRate)
        assertEquals(0.0, note.first().toDouble(), 0.0, "first sample")
        assertEquals(0.0, note.last().toDouble(), 0.0, "last sample")

        val oneMillisecond = sampleRate / 1000
        val head = (0 until oneMillisecond).maxOf { abs(note[it]) }
        val tail = (note.size - oneMillisecond until note.size).maxOf { abs(note[it]) }
        assertTrue(head < 0.1 * PluckedToneSynth.PEAK, "first millisecond peaks at $head")
        assertTrue(tail < 0.1 * PluckedToneSynth.PEAK, "last millisecond peaks at $tail")

        // No step in the fades is larger than the biggest step in the body of the note.
        val bodyStep = (1 until note.size).maxOf { abs(note[it] - note[it - 1]) }
        val fadeStep = (1 until oneMillisecond).maxOf { abs(note[it] - note[it - 1]) }
        assertTrue(fadeStep <= bodyStep, "fade-in step $fadeStep exceeds the loudest step in the note, $bodyStep")
    }

    @Test
    fun theEnvelopeRisesFromZeroAndDecaysBackToIt() {
        val note = PluckedToneSynth.render(146.83, sampleRate)
        val tenth = note.size / 10
        val opening = rms(note, 0, tenth)
        val ending = rms(note, note.size - tenth, tenth)

        assertTrue(opening > 0.3, "opening RMS $opening is too quiet to be a note")
        assertTrue(ending < 0.1 * opening, "the tail (RMS $ending) has not decayed away from the opening $opening")

        // Monotone decay: each tenth of the note is quieter than the one before it.
        val levels = (0 until 10).map { rms(note, it * tenth, tenth) }
        levels.zipWithNext { loud, quiet ->
            assertTrue(quiet < loud, "level rose from $loud to $quiet inside the decay: $levels")
        }
    }

    @Test
    fun theToneIsHarmonicallyRichAndTheHighPartialsDieFirst() {
        val f0 = 110.0
        val note = PluckedToneSynth.render(f0, sampleRate)
        val early = window(note, (0.05 * sampleRate).toInt(), ANALYSIS_WINDOW)
        val late = window(note, (0.90 * sampleRate).toInt(), ANALYSIS_WINDOW)

        val earlyFundamental = partialLevel(early, f0)
        val earlySecond = partialLevel(early, 2 * f0)
        val earlyThird = partialLevel(early, 3 * f0)

        // Rich, but the fundamental still owns the note so nothing can hear it an octave or a fifth out.
        assertTrue(earlySecond > 0.1 * earlyFundamental, "second partial $earlySecond is inaudible: not a pluck")
        assertTrue(earlyThird > 0.05 * earlyFundamental, "third partial $earlyThird is inaudible: not a pluck")
        assertTrue(earlySecond < earlyFundamental, "second partial $earlySecond outweighs the fundamental")
        assertTrue(earlyThird < earlyFundamental, "third partial $earlyThird outweighs the fundamental")

        val earlyRatio = earlyThird / earlyFundamental
        val lateRatio = partialLevel(late, 3 * f0) / partialLevel(late, f0)
        assertTrue(lateRatio < 0.5 * earlyRatio, "third partial faded from $earlyRatio to $lateRatio, not fast enough")
    }

    @Test
    fun lengthFollowsTheRequestedDuration() {
        assertEquals(sampleRate, PluckedToneSynth.render(110.0, sampleRate, 1.0).size)
        assertEquals(sampleRate / 2, PluckedToneSynth.render(110.0, sampleRate, 0.5).size)
        assertEquals(
            (PluckedToneSynth.DEFAULT_DURATION_SECONDS * sampleRate).toInt(),
            PluckedToneSynth.render(110.0, sampleRate).size,
        )
    }

    @Test
    fun partialsAboveNyquistAreDroppedInsteadOfAliasing() {
        val f0 = 9000.0
        val note = PluckedToneSynth.render(f0, sampleRate)
        assertEquals(PluckedToneSynth.PEAK, note.maxOf { abs(it) }.toDouble(), 1e-4)

        // Only the fundamental and the second partial fit below 24 kHz; the rest must not fold back down.
        val frame = window(note, (0.05 * sampleRate).toInt(), ANALYSIS_WINDOW)
        val fundamental = partialLevel(frame, f0)
        val folded = partialLevel(frame, sampleRate - 4 * f0) // where the 4th partial would alias to
        assertTrue(folded < 0.01 * fundamental, "aliased energy $folded against a fundamental of $fundamental")
    }

    @Test
    fun invalidArgumentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { PluckedToneSynth.render(0.0, sampleRate) }
        assertFailsWith<IllegalArgumentException> { PluckedToneSynth.render(110.0, 0) }
        assertFailsWith<IllegalArgumentException> { PluckedToneSynth.render(110.0, sampleRate, 0.0) }
    }

    private fun window(samples: FloatArray, offset: Int, length: Int): FloatArray {
        require(offset + length <= samples.size) { "window $offset..${offset + length} outside ${samples.size}" }
        return samples.copyOfRange(offset, offset + length)
    }

    private fun rms(samples: FloatArray, offset: Int, length: Int): Double {
        var sum = 0.0
        for (i in offset until offset + length) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / length)
    }

    /**
     * Amplitude at [frequencyHz] by Goertzel over a Hann-windowed frame. Hann keeps the neighbouring partials
     * from leaking into the bin, which matters because the partials are only ~110 Hz apart.
     */
    private fun partialLevel(frame: FloatArray, frequencyHz: Double): Double {
        val n = frame.size
        val coefficient = 2.0 * cos(2.0 * PI * frequencyHz / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            val s0 = frame[i] * hann + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }
        return sqrt(abs(s1 * s1 + s2 * s2 - coefficient * s1 * s2)) * 2.0 / n
    }

    private companion object {
        const val ANALYSIS_WINDOW = 4096
    }
}
