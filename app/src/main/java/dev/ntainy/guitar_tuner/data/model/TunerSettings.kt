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
    val activeTuningId: String = PresetIds.STANDARD,
) {
    companion object {
        const val MIN_A4_HZ = 415.0
        const val MAX_A4_HZ = 466.0
        const val MIN_TOLERANCE_CENTS = 1.0
        const val MAX_TOLERANCE_CENTS = 10.0
    }
}
