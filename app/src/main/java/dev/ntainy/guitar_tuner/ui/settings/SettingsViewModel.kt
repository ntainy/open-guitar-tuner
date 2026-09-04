package dev.ntainy.guitar_tuner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ntainy.guitar_tuner.BuildConfig
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.InputPolicy
import dev.ntainy.guitar_tuner.data.model.ThemeMode
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.dsp.Notation
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: TunerSettings = TunerSettings(),
    val availableInputs: List<AudioInputDevice> = emptyList(),
    val appVersion: String = "",
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    engine: TunerEngine,
    private val appVersion: String = BuildConfig.VERSION_NAME,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.settings,
            engine.state.map { it.availableInputs }.distinctUntilChanged(),
        ) { prefs, inputs ->
            SettingsUiState(settings = prefs, availableInputs = inputs, appVersion = appVersion)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SettingsUiState(appVersion = appVersion),
        )

    /** Reference pitch in whole Hz, clamped to the allowed range. */
    fun setA4Hz(hz: Double) = write { it.copy(a4Hz = hz.snapA4()) }

    /** Moves the reference pitch by [deltaHz] whole Hz, stopping at the range ends. */
    fun stepA4Hz(deltaHz: Int) = write { it.copy(a4Hz = (it.a4Hz + deltaHz).snapA4()) }

    fun resetA4Hz() = write { it.copy(a4Hz = DEFAULT_A4_HZ) }

    fun setToleranceCents(cents: Double) = write {
        it.copy(
            toleranceCents = cents.roundToInt().toDouble()
                .coerceIn(TunerSettings.MIN_TOLERANCE_CENTS, TunerSettings.MAX_TOLERANCE_CENTS),
        )
    }

    fun setNotation(notation: Notation) = write { it.copy(notation = notation) }

    fun setHeadstockLayout(layout: HeadstockLayout) = write { it.copy(headstockLayout = layout) }

    fun setTheme(theme: ThemeMode) = write { it.copy(theme = theme) }

    fun setShowHz(show: Boolean) = write { it.copy(showHz = show) }

    fun setKeepScreenOn(keep: Boolean) = write { it.copy(keepScreenOn = keep) }

    fun setInputPolicy(policy: InputPolicy) = write { it.copy(inputPolicy = policy) }

    /** Chooses a specific device: switches the policy to [InputPolicy.SPECIFIC_DEVICE] and remembers its key. */
    fun setPreferredInput(key: String) = write { it.copy(inputPolicy = InputPolicy.SPECIFIC_DEVICE, preferredInputKey = key) }

    private fun write(transform: (TunerSettings) -> TunerSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private fun Double.snapA4(): Double = roundToInt().toDouble().coerceIn(TunerSettings.MIN_A4_HZ, TunerSettings.MAX_A4_HZ)

    private companion object {
        const val DEFAULT_A4_HZ = 440.0
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
