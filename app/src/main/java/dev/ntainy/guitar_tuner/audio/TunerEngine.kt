package dev.ntainy.guitar_tuner.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the Tune screen renders. Produced ~25 times per second while running.
 *
 * @property pitchHz smoothed detected frequency, null when there is no stable signal
 * @property level RMS of the last capture chunk after the input's sensitivity, 0..1
 * @property gateLevel level a chunk must reach for its pitch to be trusted (the noise gate's threshold, on the
 *   same scale as [level]); together they draw the input meter
 * @property targetIndex index of the string being tuned (0 = low string), null before anything is detected
 * @property centsOff signed offset of [pitchHz] from the target string; positive = sharp
 * @property inTune true while |centsOff| <= tolerance
 * @property tunedStrings strings that have been held in tune long enough since the last "Start over"
 * @property autoMode true when the target string follows the detected pitch; false when the user pinned one
 * @property chromaticMidi in chromatic mode, the nearest MIDI note to [pitchHz]; null in every string tuning
 */
data class TunerState(
    val pitchHz: Double? = null,
    val confidence: Double = 0.0,
    val level: Double = 0.0,
    val gateLevel: Double = 0.0,
    val targetIndex: Int? = null,
    val centsOff: Double? = null,
    val inTune: Boolean = false,
    val tunedStrings: Set<Int> = emptySet(),
    val autoMode: Boolean = true,
    val chromaticMidi: Int? = null,
    val input: AudioInputDevice? = null,
    val availableInputs: List<AudioInputDevice> = emptyList(),
    val permissionGranted: Boolean = false,
    val running: Boolean = false,
    val error: String? = null,
)

/**
 * Owns capture + analysis and exposes [TunerState].
 *
 * The engine observes the active tuning and the settings on its own (constructor flows) so callers only
 * drive lifecycle and user intent. Wave 1 implementation: `AudioTunerEngine`.
 */
interface TunerEngine {
    val state: StateFlow<TunerState>

    /** Begin capturing (no-op without permission; the engine records the permission state). */
    fun start()

    fun stop()

    /** Pin a string; turns AUTO off. */
    fun selectString(index: Int)

    fun setAutoMode(enabled: Boolean)

    /** Clear the tuned marks. */
    fun startOver()

    /** Pick an input explicitly for this session; null returns to the settings policy. */
    fun selectInput(device: AudioInputDevice?)

    fun onPermissionResult(granted: Boolean)
}
