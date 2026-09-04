package dev.ntainy.guitar_tuner.fakes

import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.audio.TunerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Engine stand-in for previews and UI tests: no audio, state is whatever you [set].
 * Commands mutate the state the way the real engine would so screens can be exercised end to end.
 */
class FakeTunerEngine(initial: TunerState = TunerState(permissionGranted = true)) : TunerEngine {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<TunerState> = _state.asStateFlow()

    fun set(state: TunerState) {
        _state.value = state
    }

    fun update(transform: (TunerState) -> TunerState) = _state.update(transform)

    override fun start() = _state.update { it.copy(running = it.permissionGranted) }

    override fun stop() = _state.update { it.copy(running = false, pitchHz = null, centsOff = null, inTune = false) }

    override fun selectString(index: Int) = _state.update { it.copy(targetIndex = index, autoMode = false) }

    override fun setAutoMode(enabled: Boolean) = _state.update { it.copy(autoMode = enabled) }

    override fun startOver() = _state.update { it.copy(tunedStrings = emptySet()) }

    override fun selectInput(device: AudioInputDevice?) = _state.update { it.copy(input = device ?: it.availableInputs.firstOrNull()) }

    override fun onPermissionResult(granted: Boolean) = _state.update { it.copy(permissionGranted = granted) }
}
