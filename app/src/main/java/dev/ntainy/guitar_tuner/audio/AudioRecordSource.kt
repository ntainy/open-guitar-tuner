package dev.ntainy.guitar_tuner.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import dev.ntainy.guitar_tuner.dsp.DspDefaults
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** Raised when capture cannot start or dies mid-stream. The message is written for the screen. */
class AudioSourceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * [AudioSource] over [AudioRecord]: mono float PCM at [sampleRate] from the built-in mic or a USB interface.
 *
 * The recorder uses `UNPROCESSED` when the device advertises it (no AGC / noise suppression, which matter for
 * pitch tracking) and `VOICE_RECOGNITION` otherwise, and is routed with `setPreferredDevice` to the
 * [AudioDeviceInfo] whose id matches [device]. Reads run on a dedicated thread at
 * [Process.THREAD_PRIORITY_URGENT_AUDIO] with blocking reads; each chunk is a fresh array handed to the
 * collector through a small [BufferOverflow.DROP_OLDEST] buffer, so a slow collector loses chunks rather than
 * stalling the recorder. Cancelling the collector stops and releases the recorder. Initialisation, start or
 * read failures close the flow with an [AudioSourceException].
 *
 * @param chunkSize samples per emitted array
 */
class AudioRecordSource(
    context: Context,
    override val device: AudioInputDevice,
    override val sampleRate: Int = DspDefaults.SAMPLE_RATE,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) : AudioSource {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(chunkSize > 0) { "chunkSize must be positive, was $chunkSize" }
    }

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager = appContext.getSystemService(AudioManager::class.java)

    override fun samples(): Flow<FloatArray> = callbackFlow {
        val record = openRecord()
        val active = AtomicBoolean(true)
        val reader = Thread({ readLoop(record, active) }, "AudioRecordSource")
        reader.start()
        awaitClose {
            active.set(false)
            reader.join(READER_JOIN_TIMEOUT_MS)
            Log.d(TAG, "Capture stopped on ${device.name}")
        }
    }.buffer(capacity = BUFFERED_CHUNKS, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Builds and configures the recorder; throws [AudioSourceException] with a user-facing message on failure. */
    private fun openRecord(): AudioRecord {
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val audioSource =
            if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuffer <= 0) {
            throw AudioSourceException("$sampleRate Hz mono float capture is not supported on this device")
        }
        val bufferBytes = max(minBuffer, 4 * chunkSize * Float.SIZE_BYTES)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val record = try {
            AudioRecord.Builder()
                .setContext(appContext)
                .setAudioSource(audioSource)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (e: SecurityException) {
            throw AudioSourceException("Microphone permission is missing", e)
        } catch (e: RuntimeException) {
            throw AudioSourceException("Could not open ${device.name}: ${e.message ?: e::class.java.simpleName}", e)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AudioSourceException("Could not initialise capture on ${device.name}")
        }
        val info = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == device.id }
        when {
            info == null ->
                Log.w(TAG, "${device.name} (id ${device.id}) is not connected; using the system default input")

            !record.setPreferredDevice(info) ->
                Log.w(TAG, "setPreferredDevice(${device.name}) was rejected; using the system default input")
        }
        Log.d(
            TAG,
            "AudioRecord opened: source=${sourceName(audioSource)}, device=${device.name} [${device.key}], " +
                "$sampleRate Hz mono float, buffer=$bufferBytes bytes (min $minBuffer), chunk=$chunkSize",
        )
        return record
    }

    /** Runs on the capture thread until [active] is cleared or the recorder fails. Always releases [record]. */
    private fun ProducerScope<FloatArray>.readLoop(record: AudioRecord, active: AtomicBoolean) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw AudioSourceException("Recording did not start on ${device.name}")
            }
            Log.d(TAG, "Recording on ${device.name}; routed to ${describe(record.routedDevice)}")
            while (active.get()) {
                val chunk = FloatArray(chunkSize)
                val read = record.read(chunk, 0, chunkSize, AudioRecord.READ_BLOCKING)
                when {
                    read == chunkSize -> trySend(chunk)
                    read > 0 -> trySend(chunk.copyOf(read))
                    read == 0 -> if (active.get() && record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw AudioSourceException("Recording on ${device.name} stopped unexpectedly")
                    }

                    else -> if (active.get()) {
                        throw AudioSourceException("Reading from ${device.name} failed: ${errorName(read)}")
                    }
                }
            }
            close()
        } catch (e: AudioSourceException) {
            Log.e(TAG, e.message ?: "capture failed", e)
            close(e)
        } catch (e: RuntimeException) {
            val wrapped = AudioSourceException(
                "Capture on ${device.name} failed: ${e.message ?: e::class.java.simpleName}",
                e,
            )
            Log.e(TAG, wrapped.message ?: "capture failed", e)
            close(wrapped)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun describe(info: AudioDeviceInfo?): String =
        if (info == null) "unknown device" else "${info.productName} (type ${info.type}, id ${info.id})"

    companion object {
        const val TAG = "TunerEngine"
        const val DEFAULT_CHUNK_SIZE = 1024

        /** Chunks the collector may fall behind before the oldest is dropped (~85 ms at 1024 / 48 kHz). */
        private const val BUFFERED_CHUNKS = 4
        private const val READER_JOIN_TIMEOUT_MS = 500L

        private fun sourceName(source: Int): String = when (source) {
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            else -> "source#$source"
        }

        private fun errorName(code: Int): String = when (code) {
            AudioRecord.ERROR_INVALID_OPERATION -> "invalid operation"
            AudioRecord.ERROR_BAD_VALUE -> "bad value"
            AudioRecord.ERROR_DEAD_OBJECT -> "device disconnected"
            else -> "error $code"
        }
    }
}
