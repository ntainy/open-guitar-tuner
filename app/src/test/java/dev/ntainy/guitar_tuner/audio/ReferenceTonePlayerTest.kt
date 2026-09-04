package dev.ntainy.guitar_tuner.audio

import dev.ntainy.guitar_tuner.dsp.DspDefaults
import dev.ntainy.guitar_tuner.dsp.PluckedToneSynth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Drives [AudioTrackReferenceTonePlayer] through every way a note can end. The real `AudioTrack` cannot be built
 * in a JVM unit test, so the platform seam is a [FakeOutput]; what is under test here is the state machine —
 * `playing` never sticking, one track alive at a time, every track released. The waveform itself is proven in
 * `:dsp` by `PluckedToneSynthTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceTonePlayerTest {

    @Test
    fun playingIsTrueForExactlyTheNoteAndTheTrackIsReleasedAfterwards() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        assertFalse(player.playing.value, "quiet before the first note")

        val call = launch { player.play(A2_HZ) }
        runCurrent()
        assertTrue(player.playing.value, "playing did not go true when the note started")
        assertEquals(1, output.tracks.size)
        assertEquals(1, output.tracks[0].played, "the track was never started")

        advanceTimeBy(player.noteDurationMillis - 1)
        runCurrent()
        assertTrue(player.playing.value, "the note ended early, at $currentTime ms")
        assertTrue(call.isActive, "play() returned before the note finished")
        assertEquals(0, output.tracks[0].released, "the track was released while still sounding")

        advanceTimeBy(2)
        runCurrent()
        assertFalse(player.playing.value, "playing stuck true after the note ended")
        assertTrue(call.isCompleted, "play() did not return when the note ended")
        assertEquals(1, output.tracks[0].released, "the track was not released")
        assertEquals(1, output.maxAlive)
    }

    @Test
    fun theBufferHandedToTheTrackIsTheRequestedNote() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        launch { player.play(A2_HZ) }
        runCurrent()

        val track = output.tracks.single()
        assertEquals(DspDefaults.SAMPLE_RATE, track.sampleRate)
        assertContentEquals(
            PluckedToneSynth.render(A2_HZ, DspDefaults.SAMPLE_RATE, DURATION_SECONDS),
            track.samples,
            "the player rendered something other than the requested note",
        )
        assertTrue(track.samples.all { abs(it) <= 1f }, "the buffer clips")
        assertTrue(player.noteDurationMillis >= (DURATION_SECONDS * 1000).toLong(), "the note is cut short")
    }

    @Test
    fun stopEndsTheNoteEarlyAndReleasesTheTrack() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        val call = launch { player.play(A2_HZ) }
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        player.stop()
        assertFalse(player.playing.value, "playing must drop as soon as stop() is called")

        runCurrent()
        assertTrue(call.isCompleted, "play() did not return after stop()")
        assertEquals(1, output.tracks[0].released)
        assertTrue(currentTime < player.noteDurationMillis, "stop() waited for the whole note")
    }

    @Test
    fun stopOnASilentPlayerIsHarmless() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        player.stop()
        player.stop()
        runCurrent()
        assertFalse(player.playing.value)
        assertTrue(output.tracks.isEmpty())
    }

    @Test
    fun cancellingTheCallerStopsTheNoteAndClearsPlaying() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        val call = launch { player.play(A2_HZ) }
        runCurrent()
        assertTrue(player.playing.value)

        call.cancelAndJoin()
        assertFalse(player.playing.value, "playing stuck true after the caller was cancelled")

        runCurrent()
        assertEquals(1, output.tracks[0].released, "the track leaked when the caller was cancelled")
        assertEquals(0, output.alive)
    }

    @Test
    fun aSecondPlayReplacesTheFirstInsteadOfStacking() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        val first = launch { player.play(A2_HZ) }
        runCurrent()
        val second = launch { player.play(E4_HZ) }
        runCurrent()

        assertTrue(first.isCompleted, "the first note is still running")
        assertFalse(first.isCancelled, "a superseded play() must return, not throw")
        assertEquals(2, output.tracks.size)
        assertEquals(1, output.maxAlive, "two notes were sounding at once")
        assertEquals(1, output.tracks[0].released, "the replaced track was not released")
        assertTrue(player.playing.value, "the replacement note is not sounding")

        advanceTimeBy(player.noteDurationMillis + 1)
        runCurrent()
        assertTrue(second.isCompleted, "the replacement note never finished")
        assertFalse(player.playing.value)
        assertEquals(0, output.alive)
    }

    @Test
    fun rapidPressesNeitherStackNorLeak() = runTest {
        val output = FakeOutput()
        val player = player(output, backgroundScope)
        repeat(12) {
            launch { player.play(A2_HZ) }
            advanceTimeBy(15)
            runCurrent()
        }
        advanceTimeBy(player.noteDurationMillis + 1)
        runCurrent()

        assertEquals(1, output.maxAlive, "notes stacked up")
        assertEquals(0, output.alive, "a track leaked")
        assertTrue(output.tracks.all { it.released == 1 }, "a track was released more or less than once")
        assertFalse(player.playing.value)
    }

    @Test
    fun aRefusedOutputLeavesPlayingFalse() = runTest {
        val output = FakeOutput(refuse = true)
        val player = player(output, backgroundScope)
        player.play(A2_HZ)

        assertFalse(player.playing.value, "playing went true without an output device")
        assertTrue(output.tracks.isEmpty())
    }

    @Test
    fun anOutputFailureIsSwallowedAndLeavesPlayingFalse() = runTest {
        val output = FakeOutput(failure = IllegalStateException("AudioTrack died"))
        val player = player(output, backgroundScope)
        player.play(A2_HZ)

        assertFalse(player.playing.value, "playing stuck true after the output failed")
        assertTrue(output.tracks.isEmpty())
    }

    private fun player(output: FakeOutput, scope: CoroutineScope) = AudioTrackReferenceTonePlayer(
        scope = scope,
        openTrack = output::open,
        sampleRate = DspDefaults.SAMPLE_RATE,
        durationSeconds = DURATION_SECONDS,
    )

    /** Stands in for the device's media output; counts how many tracks are open at once. */
    private class FakeOutput(private val refuse: Boolean = false, private val failure: RuntimeException? = null) {
        val tracks = mutableListOf<FakeTrack>()
        var alive = 0
            private set
        var maxAlive = 0
            private set

        fun open(samples: FloatArray, sampleRate: Int): ToneTrack? {
            failure?.let { throw it }
            if (refuse) return null
            alive++
            maxAlive = maxOf(maxAlive, alive)
            return FakeTrack(samples, sampleRate, onRelease = { alive-- }).also { tracks += it }
        }
    }

    private class FakeTrack(
        val samples: FloatArray,
        val sampleRate: Int,
        private val onRelease: () -> Unit,
    ) : ToneTrack {
        var played = 0
            private set
        var released = 0
            private set

        override fun play() {
            played++
        }

        override fun release() {
            released++
            onRelease()
        }
    }

    private companion object {
        const val A2_HZ = 110.0
        const val E4_HZ = 329.6275569128699
        const val DURATION_SECONDS = 1.2
    }
}
