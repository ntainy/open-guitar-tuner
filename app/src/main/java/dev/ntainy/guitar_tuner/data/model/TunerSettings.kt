package dev.ntainy.guitar_tuner.data.model

import dev.ntainy.guitar_tuner.dsp.Notation
import kotlinx.serialization.Serializable

@Serializable
enum class InputPolicy {
    /** Use a USB audio input when one is connected, otherwise the built-in mic. */
    PREFER_USB,

    /** Always the built-in mic. */
    BUILTIN_MIC,

    /** The device whose key equals [TunerSettings.preferredInputKey]; falls back to PREFER_USB when absent. */
    SPECIFIC_DEVICE,
}

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
enum class HeadstockLayout(val label: String) {
    THREE_PLUS_THREE("3+3"),
    SIX_IN_LINE("6-in-line"),
}

/** Everything the user can change in Settings, persisted as one JSON document. */
@Serializable
data class TunerSettings(
    val a4Hz: Double = 440.0,
    val toleranceCents: Double = 5.0,
    val inputPolicy: InputPolicy = InputPolicy.PREFER_USB,
    val preferredInputKey: String? = null,
    val notation: Notation = Notation.SHARPS,
    val headstockLayout: HeadstockLayout = HeadstockLayout.THREE_PLUS_THREE,
    val keepScreenOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val showHz: Boolean = true,
    val haptics: Boolean = true,
    val showTrace: Boolean = true,
    val activeTuningId: String = PresetIds.STANDARD,
    /**
     * "Sensitivity": gain in dB applied to an input's samples before analysis, keyed by the input's stable key
     * (`AudioInputDevice.key`). An input with no entry runs at 0 dB. Per input because it corrects for the
     * interface, not for the player: a dry pickup through a USB interface can sit 30 dB below a phone mic.
     */
    val sensitivityDb: Map<String, Double> = emptyMap(),
) {
    /** Sensitivity for the input with [key], 0 dB when nothing has been set (or there is no input). */
    fun sensitivityFor(key: String?): Double = key?.let { sensitivityDb[it] } ?: 0.0

    /** These settings with [key]'s sensitivity at [db]; 0 dB drops the entry, so only inputs that differ are listed. */
    fun withSensitivity(key: String, db: Double): TunerSettings =
        copy(sensitivityDb = if (db == 0.0) sensitivityDb - key else sensitivityDb + (key to db))

    companion object {
        const val MIN_A4_HZ = 415.0
        const val MAX_A4_HZ = 466.0
        const val MIN_TOLERANCE_CENTS = 1.0
        const val MAX_TOLERANCE_CENTS = 10.0

        /** Sensitivity range: enough boost to lift a dry pickup out of the gate, a little cut for a hot interface. */
        const val MIN_SENSITIVITY_DB = -12.0
        const val MAX_SENSITIVITY_DB = 36.0
    }
}
