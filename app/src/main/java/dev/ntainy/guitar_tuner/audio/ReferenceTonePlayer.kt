package dev.ntainy.guitar_tuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import dev.ntainy.guitar_tuner.dsp.DspDefaults
import dev.ntainy.guitar_tuner.dsp.PluckedToneSynth
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Plays a string's target pitch back so it can be tuned by ear. */
interface ReferenceTonePlayer {
    /** True while a tone is sounding; the Tune screen pauses capture on this. */
    val playing: StateFlow<Boolean>

    /** Play a plucked note at [frequencyHz]. Suspends until the note has finished. A new call replaces the current note. */
    suspend fun play(frequencyHz: Double)

    fun stop()
}

/**
 * The one-shot output device a note is written to. Exists so [AudioTrackReferenceTonePlayer]'s state machine can
 * be unit-tested without a real [AudioTrack] (which cannot be built under `unitTests.isReturnDefaultValues`).
 */
interface ToneTrack {
    /** Starts playing the samples the track was opened with. */
    fun play()

    /** Stops playback and frees the device. Called exactly once per track, including when the note is cancelled. */
    fun release()
}

/**
 * [ReferenceTonePlayer] over a `MODE_STATIC` [AudioTrack]: the whole note is rendered by
 * [PluckedToneSynth] up front, written into one static buffer and played as a single shot, so there is no
 * streaming callback to keep fed and nothing to underrun.
 *
 * Lifecycle. Each note runs as its own job in [scope], not in the caller's coroutine, so the three ways a note
 * can end all converge on the same `finally`, which releases the track and clears [playing]:
 * - it plays out (`play` returns after [noteDurationMillis]);
 * - [stop] cancels it;
 * - the caller's coroutine is cancelled — the screen going away mid-note — and `play`'s own `finally` cancels it.
 *
 * A second [play] does not stack: it cancels the note that is sounding and waits for that note's `finally` to run
 * before starting its own, so exactly one [AudioTrack] is alive at a time however fast the button is pressed. The
 * superseded call returns normally rather than throwing, so the caller sees an ordinary short note.
 *
 * @param scope where notes run; must not be the main dispatcher, since opening a track and writing the buffer block
 * @param openTrack the platform seam; returns null when no output device can be opened
 * @param sampleRate output rate, matching the analysis chain
 * @param durationSeconds length of the rendered note
 */
class AudioTrackReferenceTonePlayer(
    private val scope: CoroutineScope,
    private val openTrack: (FloatArray, Int) -> ToneTrack?,
    private val sampleRate: Int = DspDefaults.SAMPLE_RATE,
    private val durationSeconds: Double = PluckedToneSynth.DEFAULT_DURATION_SECONDS,
) : ReferenceTonePlayer {

    /**
     * The production constructor: notes go to the device's media output.
     *
     * @param appContext application context, handed to [AudioTrack.Builder.setContext] for routing and attribution
     */
    constructor(
        appContext: Context,
        scope: CoroutineScope,
        sampleRate: Int = DspDefaults.SAMPLE_RATE,
        durationSeconds: Double = PluckedToneSynth.DEFAULT_DURATION_SECONDS,
    ) : this(
        scope = scope,
        openTrack = { samples, rate -> openAudioTrack(appContext, samples, rate) },
        sampleRate = sampleRate,
        durationSeconds = durationSeconds,
    )

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(durationSeconds > 0.0) { "durationSeconds must be positive, was $durationSeconds" }
    }

    /** How long one [play] occupies: the rendered note plus [LATENCY_GRACE_MS] so its tail is not cut off. */
    val noteDurationMillis: Long = ceil(durationSeconds * MILLIS_PER_SECOND).toLong() + LATENCY_GRACE_MS

    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** The note that owns the output right now. Guarded by [lock]; only [play] clears it. */
    private var currentNote: Job? = null
    private val lock = Any()

    override suspend fun play(frequencyHz: Double) {
        require(frequencyHz > 0.0) { "frequencyHz must be positive, was $frequencyHz" }
        val note = scope.launch(start = CoroutineStart.LAZY) { sound(frequencyHz) }
        val previous = synchronized(lock) { currentNote.also { currentNote = note } }
        try {
            // Let the note being replaced finish releasing its track before this one grabs the output.
            previous?.cancelAndJoin()
            note.start()
            note.join()
        } finally {
            if (!note.isCompleted) {
                // The caller was cancelled: stop the sound and clear the flag now rather than waiting for the
                // note's own finally, which the screen may never see.
                note.cancel()
                _playing.value = false
            }
            synchronized(lock) { if (currentNote === note) currentNote = null }
        }
    }

    override fun stop() {
        val note = synchronized(lock) { currentNote } ?: return
        note.cancel()
        _playing.value = false
        Log.d(TAG, "Stopped")
    }

    /** One note, start to finish. Runs in [scope]; every exit path releases the track and clears [playing]. */
    private suspend fun sound(frequencyHz: Double) {
        val samples = PluckedToneSynth.render(frequencyHz, sampleRate, durationSeconds)
        val track = try {
            openTrack(samples, sampleRate)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open an output for $frequencyHz Hz", e)
            null
        }
        if (track == null) {
            Log.w(TAG, "No reference tone for $frequencyHz Hz: no output device")
            return
        }
        _playing.value = true
        try {
            Log.d(TAG, "Playing $frequencyHz Hz, ${samples.size} samples, ${noteDurationMillis}ms")
            track.play()
            delay(noteDurationMillis)
        } finally {
            runCatching { track.release() }
            _playing.value = false
        }
    }

    companion object {
        const val TAG = "ReferenceTone"

        /** Slack after the rendered note so output latency cannot swallow its release ramp. */
        const val LATENCY_GRACE_MS = 120L

        private const val MILLIS_PER_SECOND = 1_000.0

        /**
         * Builds a `MODE_STATIC` [AudioTrack], fills it with [samples] and hands it back ready to play.
         * Returns null when the device refuses 48 kHz mono float output or the buffer cannot be filled.
         */
        private fun openAudioTrack(context: Context, samples: FloatArray, sampleRate: Int): ToneTrack? {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setContext(context)
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            // A static track holds STATE_NO_STATIC_DATA until its buffer is written, then STATE_INITIALIZED.
            if (track.state != AudioTrack.STATE_NO_STATIC_DATA && track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack came up in state ${track.state}")
                track.release()
                return null
            }
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written < samples.size) {
                Log.w(TAG, "Wrote $written of ${samples.size} samples into the static buffer")
                track.release()
                return null
            }
            return AudioTrackToneTrack(track)
        }
    }

    /** [ToneTrack] over a real [AudioTrack]. Idempotent release: the note's `finally` may run more than once. */
    private class AudioTrackToneTrack(private val track: AudioTrack) : ToneTrack {
        private var released = false

        override fun play() {
            track.play()
        }

        override fun release() {
            if (released) return
            released = true
            runCatching { track.stop() }
            track.release()
        }
    }
}
