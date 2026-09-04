package dev.ntainy.guitar_tuner.audio

import kotlinx.coroutines.flow.Flow

/**
 * A stream of mono float samples in [-1, 1].
 *
 * [samples] is a cold flow: capture starts when it is collected and stops when the collector is cancelled.
 * Each emitted array is a fresh chunk owned by the collector (never reused by the source). Chunk size is
 * the source's choice, typically 1024 samples.
 *
 * Wave 1 implementations: `AudioRecordSource` (mic or USB via `setPreferredDevice`), `SyntheticToneSource`
 * (debug builds; a plucked-string-like tone at an adjustable frequency).
 */
interface AudioSource {
    val sampleRate: Int

    val device: AudioInputDevice

    fun samples(): Flow<FloatArray>
}
