package dev.ntainy.guitar_tuner.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.model.Tuning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Tune screen state holder. Combines the engine, settings, the active tuning and the test-tone frequency into one
 * [TunerUiState]; every command is forwarded to the [TunerEngine], which owns the tuning logic.
 */
class TunerViewModel(
    private val engine: TunerEngine,
    settings: SettingsRepository,
    activeTuning: Flow<Tuning>,
    private val testTone: MutableStateFlow<Double>,
) : ViewModel() {

    /** The input the user picked in the sheet for this session; null = "Auto" (the settings policy decides). */
    private val selectedInputKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TunerUiState> =
        combine(engine.state, settings.settings, activeTuning, testTone, selectedInputKey, ::deriveTunerUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TunerUiState())

    fun start() = engine.start()

    fun stop() = engine.stop()

    /** Pin a string; the engine turns AUTO off. */
    fun selectString(index: Int) = engine.selectString(index)

    /** A tap on a string button: pin it, or tap the pinned string again to hand control back to AUTO. */
    fun onStringTap(index: Int) {
        val engineState = engine.state.value
        if (!engineState.autoMode && engineState.targetIndex == index) engine.setAutoMode(true) else engine.selectString(index)
    }

    fun setAutoMode(enabled: Boolean) = engine.setAutoMode(enabled)

    fun startOver() = engine.startOver()

    /** Explicit input for this session, or null to return to the settings policy ("Auto"). */
    fun selectInput(device: AudioInputDevice?) {
        selectedInputKey.value = device?.key
        engine.selectInput(device)
    }

    fun onPermissionResult(granted: Boolean) = engine.onPermissionResult(granted)

    fun setTestToneHz(hz: Double) {
        testTone.value = hz.coerceIn(TunerUiState.TEST_TONE_MIN_HZ, TunerUiState.TEST_TONE_MAX_HZ)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
