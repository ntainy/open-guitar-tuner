package dev.ntainy.guitar_tuner.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.ReferenceTonePlayer
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.STRING_COUNT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * One-shot things the screen reacts to once, tied to a real state transition rather than to recomposition.
 * The screen decides how each is expressed: [AllTuned] is the only one that is also visible.
 */
sealed interface TunerEvent {
    /** Every string of the tuning has just earned its tuned mark: snackbar plus the firmest haptic. */
    data object AllTuned : TunerEvent

    /** One more string just earned its tuned mark. */
    data object StringTuned : TunerEvent

    /** The needle has just crossed into the in-tune band. Rate-limited, so a wobbling needle does not buzz. */
    data object EnteredBand : TunerEvent
}

/**
 * Tune screen state holder. Combines the engine, settings, the active tuning and the test-tone frequency into one
 * [TunerUiState]; every command is forwarded to the [TunerEngine], which owns the tuning logic. Transitions worth
 * feeling or announcing leave as [TunerEvent]s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    private val engine: TunerEngine,
    private val settings: SettingsRepository,
    activeTuning: Flow<Tuning>,
    private val testTone: MutableStateFlow<Double>,
    private val referenceTone: ReferenceTonePlayer? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** True between the screen's resume and pause, so a reference tone knows whether to hand the mic back. */
    private var screenActive = false

    /** Notes can overlap when you press one string while another is still ringing; capture resumes after the last. */
    private var soundingTones = 0

    /** The input the user picked in the sheet for this session; null = "Auto" (the settings policy decides). */
    private val selectedInputKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TunerUiState> =
        combine(engine.state, settings.settings, activeTuning, testTone, selectedInputKey, ::deriveTunerUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TunerUiState())

    /**
     * The last [TRACE_WINDOW_MS] of readings, or empty while the trace is switched off.
     *
     * Sampled on a timer rather than from `engine.state`, which is a StateFlow and stops emitting when the pitch
     * stops changing: a trace that freezes in silence instead of scrolling would be lying about time. Only runs
     * while the screen is actually collecting it.
     */
    val trace: StateFlow<List<TracePoint>> =
        settings.settings
            .map { it.showTrace }
            .distinctUntilChanged()
            .flatMapLatest { enabled -> if (enabled) traceSamples() else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun traceSamples(): Flow<List<TracePoint>> = flow {
        val window = ArrayDeque<TracePoint>()
        while (true) {
            val at = now()
            window.addLast(TracePoint(at, engine.state.value.centsOff?.toFloat()))
            while (window.isNotEmpty() && at - window.first().atMs > TRACE_WINDOW_MS) window.removeFirst()
            emit(window.toList())
            delay(TRACE_SAMPLE_INTERVAL_MS)
        }
    }

    private val _events = Channel<TunerEvent>(Channel.BUFFERED)

    /** Consumed once by the screen; nothing is replayed when the screen is rebuilt. */
    val events: Flow<TunerEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { watchForFeedback() }
    }

    /**
     * Turns engine state into the one-shot [TunerEvent]s. One collector rather than three so the ordering is
     * defined: earning the last mark is "all tuned", never "one more string" as well.
     */
    private suspend fun watchForFeedback() {
        var previousTuned: Set<Int>? = null
        var wasInTune = false
        var lastBandTickAt = 0L
        engine.state.collect { state ->
            val previous = previousTuned
            previousTuned = state.tunedStrings
            // Whatever is true when this ViewModel is created was either celebrated already or never will be:
            // only transitions observed since then count.
            if (previous == null) {
                wasInTune = state.inTune
                return@collect
            }
            if ((state.tunedStrings - previous).isNotEmpty()) {
                val all = state.tunedStrings.size >= STRING_COUNT
                _events.send(if (all) TunerEvent.AllTuned else TunerEvent.StringTuned)
            }
            if (state.inTune && !wasInTune) {
                val at = now()
                if (at - lastBandTickAt >= BAND_TICK_MIN_INTERVAL_MS) {
                    lastBandTickAt = at
                    _events.send(TunerEvent.EnteredBand)
                }
            }
            wasInTune = state.inTune
        }
    }

    fun start() {
        screenActive = true
        engine.start()
    }

    fun stop() {
        screenActive = false
        engine.stop()
    }

    /**
     * Plays the target pitch of [index] so it can be matched by ear.
     *
     * Capture stops for the length of the note: left running, the tuner would happily hear the phone's own speaker
     * and tune to it. The mic comes back only after the last overlapping note and only if the screen is still up,
     * so a note finishing after the user has left does not quietly reopen the microphone.
     */
    fun playReferenceTone(index: Int) {
        val player = referenceTone ?: return
        val frequencyHz = uiState.value.strings.getOrNull(index)?.frequencyHz ?: return
        viewModelScope.launch {
            if (soundingTones++ == 0) engine.stop()
            try {
                player.play(frequencyHz)
            } finally {
                if (--soundingTones == 0 && screenActive) engine.start()
            }
        }
    }

    /** Pin a string; the engine turns AUTO off. */
    fun selectString(index: Int) = engine.selectString(index)

    /** A tap on a string button: pin it, or tap the pinned string again to hand control back to AUTO. */
    fun onStringTap(index: Int) {
        val engineState = engine.state.value
        val unpinning = !engineState.autoMode && engineState.targetIndex == index
        if (unpinning) engine.setAutoMode(true) else engine.selectString(index)
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

        /** A needle sitting on the edge of the band can cross it many times a second; only tick this often. */
        const val BAND_TICK_MIN_INTERVAL_MS = 600L

        /** 25 Hz, the rate the engine produces frames at: fast enough to look continuous, cheap enough to ignore. */
        const val TRACE_SAMPLE_INTERVAL_MS = 40L
    }
}
