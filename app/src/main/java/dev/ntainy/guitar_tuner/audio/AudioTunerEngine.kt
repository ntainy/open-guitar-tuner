package dev.ntainy.guitar_tuner.audio

import android.util.Log
import dev.ntainy.guitar_tuner.BuildConfig
import dev.ntainy.guitar_tuner.data.model.InputPolicy
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.dsp.FrameAssembler
import dev.ntainy.guitar_tuner.dsp.Harmonics
import dev.ntainy.guitar_tuner.dsp.NoiseGate
import dev.ntainy.guitar_tuner.dsp.TargetMatch
import dev.ntainy.guitar_tuner.dsp.OctaveGuard
import dev.ntainy.guitar_tuner.dsp.OnsetDetector
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.dsp.PitchDetector
import dev.ntainy.guitar_tuner.dsp.PitchSmoother
import dev.ntainy.guitar_tuner.dsp.TargetResolver
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The real [TunerEngine]: picks an input, captures it, runs the DSP chain and publishes [TunerState].
 *
 * Nothing Android-specific is referenced directly, so the engine is unit-tested on the JVM with fakes:
 * the monitor supplies devices, [sourceFactory] turns the chosen one into an [AudioSource], and the DSP
 * objects are the `:dsp` interfaces.
 *
 * Input choice: an explicit [selectInput] wins while that device is present; otherwise
 * [TunerSettings.inputPolicy] decides, and the choice is re-evaluated on every hot-plug so unplugging the
 * USB interface falls back to the mic automatically. Capture runs only while [start] has been requested and
 * permission is granted, restarts whenever the chosen device changes, and is torn down by [stop]. A source
 * failure is retried once; a second failure lands in [TunerState.error] with `running = false`, and the next
 * [start] tries again.
 *
 * Per analysis frame: `assembler` -> `detector` -> `smoother` -> target (resolver in AUTO, the pinned index
 * otherwise) -> cents / in-tune / tuned marks. A string is marked tuned after [framesToMarkTuned] consecutive
 * in-tune frames on the same target. All DSP work runs on [analysisDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioTunerEngine(
    scope: CoroutineScope,
    inputMonitor: AudioInputMonitor,
    private val sourceFactory: (AudioInputDevice) -> AudioSource,
    private val detector: PitchDetector,
    private val smoother: PitchSmoother,
    private val resolver: TargetResolver,
    private val assembler: FrameAssembler,
    tuningFlow: Flow<Tuning>,
    settingsFlow: Flow<TunerSettings>,
    analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val framesToMarkTuned: Int = 8,
    private val noiseGate: NoiseGate = NoiseGate(),
    private val onsetDetector: OnsetDetector = OnsetDetector(),
    private val octaveGuard: OctaveGuard = OctaveGuard(),
    private val holdOffFrames: Int = 7,
    private val silenceFramesToReset: Int = 12,
) : TunerEngine {

    init {
        require(framesToMarkTuned >= 1) { "framesToMarkTuned must be at least 1, was $framesToMarkTuned" }
    }

    private val _state = MutableStateFlow(TunerState())
    override val state: StateFlow<TunerState> = _state.asStateFlow()

    private val startRequested = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(false)
    private val manualInput = MutableStateFlow<AudioInputDevice?>(null)

    /**
     * Guards the DSP objects and the tuned-run counters: frames arrive on [analysisDispatcher] while commands
     * come from the caller's thread. Held for at most one frame of analysis, so commands stay main-safe.
     */
    private val dspLock = Any()

    /** String index pinned by the user while AUTO is off; null lets [resolver] choose. Guarded by [dspLock]. */
    private var pinnedString: Int? = null

    /** Analysis frames still to skip after the last attack; the reading is frozen meanwhile. Guarded by [dspLock]. */
    private var holdRemaining = 0

    /** Consecutive frames without a pitch; the resolver forgets its string after [silenceFramesToReset]. */
    private var silentFrames = 0

    /** Consecutive in-tune frames on [tunedRunTarget]. Guarded by [dspLock]. */
    private var tunedRun = 0
    private var tunedRunTarget = -1

    @Volatile
    private var lastTuning: Tuning? = null

    private val settings: StateFlow<TunerSettings> =
        settingsFlow.stateIn(scope, SharingStarted.Eagerly, TunerSettings())

    private val tuning: StateFlow<Tuning?> =
        tuningFlow.onEach(::onTuningChanged).stateIn(scope, SharingStarted.Eagerly, null)

    private val availableInputs: StateFlow<List<AudioInputDevice>> = inputMonitor.devices
        .onEach { list -> _state.update { it.copy(availableInputs = list) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val chosenInput: StateFlow<AudioInputDevice?> =
        combine(availableInputs, manualInput, settings) { devices, manual, current -> chooseInput(devices, manual, current) }
            .distinctUntilChanged()
            .onEach { device ->
                Log.d(TAG, if (device == null) "Input: none available" else "Input: ${device.name} [${device.key}] ${device.kind}")
                _state.update { it.copy(input = device) }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch(analysisDispatcher) {
            combine(startRequested, permissionGranted) { started, granted -> started && granted }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (!active) {
                        markStopped()
                        emptyFlow()
                    } else {
                        chosenInput.flatMapLatest { device ->
                            if (device == null) {
                                Log.w(TAG, "Capture requested but no input device is available")
                                markStopped()
                                emptyFlow<Unit>()
                            } else {
                                captureFrom(device)
                            }
                        }
                    }
                }
                .collect()
        }
    }

    override fun start() {
        _state.update { it.copy(error = null) }
        startRequested.value = true
    }

    override fun stop() {
        synchronized(dspLock) {
            startRequested.value = false
            resetDsp()
            markStopped()
        }
    }

    override fun selectString(index: Int) {
        synchronized(dspLock) {
            pinnedString = index
            tunedRun = 0
            _state.update { it.copy(targetIndex = index, autoMode = false) }
        }
    }

    override fun setAutoMode(enabled: Boolean) {
        synchronized(dspLock) {
            if (enabled) {
                pinnedString = null
                resolver.reset()
                _state.update { it.copy(autoMode = true) }
            } else {
                val pinned = _state.value.targetIndex ?: 0
                pinnedString = pinned
                _state.update { it.copy(autoMode = false, targetIndex = pinned) }
            }
        }
    }

    override fun startOver() {
        synchronized(dspLock) {
            tunedRun = 0
            _state.update { it.copy(tunedStrings = emptySet()) }
        }
    }

    override fun selectInput(device: AudioInputDevice?) {
        Log.d(TAG, if (device == null) "Input override cleared" else "Input override: ${device.name} [${device.key}]")
        manualInput.value = device
    }

    override fun onPermissionResult(granted: Boolean) {
        Log.d(TAG, "RECORD_AUDIO permission granted=$granted")
        _state.update { it.copy(permissionGranted = granted) }
        permissionGranted.value = granted
    }

    /** Captures [device] until cancelled. Retries once on failure, then records the error and gives up. */
    private fun captureFrom(device: AudioInputDevice): Flow<Unit> = flow {
        var attempt = 1
        while (true) {
            val source = sourceFactory(device)
            Log.d(
                TAG,
                "Capture attempt $attempt: input=${device.name} [${device.key}] " +
                    "source=${source::class.java.simpleName} at ${source.sampleRate} Hz",
            )
            if (source.sampleRate != detector.sampleRate) {
                Log.w(TAG, "Source runs at ${source.sampleRate} Hz but the detector expects ${detector.sampleRate} Hz")
            }
            synchronized(dspLock) { resetDsp() }
            _state.update { it.copy(running = true, error = null) }
            val failure: Throwable = try {
                source.samples().collect { chunk -> analyse(chunk) }
                AudioSourceException("${device.name} stopped delivering audio")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            } finally {
                synchronized(dspLock) { resetDsp() }
            }
            if (attempt < CAPTURE_ATTEMPTS) {
                Log.w(TAG, "Capture on ${device.name} failed, retrying in $RETRY_DELAY_MS ms", failure)
                clearReadings()
                attempt++
                delay(RETRY_DELAY_MS)
            } else {
                Log.e(TAG, "Capture on ${device.name} failed", failure)
                synchronized(dspLock) {
                    startRequested.value = false
                    _state.update {
                        it.copy(
                            running = false,
                            error = failure.message ?: failure::class.java.simpleName,
                            pitchHz = null,
                            confidence = 0.0,
                            level = 0.0,
                            centsOff = null,
                            inTune = false,
                        )
                    }
                }
                return@flow
            }
        }
    }

    /** One captured chunk: level, then every analysis frame the assembler completes from it. */
    private fun analyse(chunk: FloatArray) {
        val level = rms(chunk)
        synchronized(dspLock) {
            if (!startRequested.value) return
            noiseGate.observe(level)
            if (onsetDetector.push(level)) {
                // A new attack: its first ~300 ms carry a sharp, unstable transient. Freeze the reading until it
                // has passed, then start the smoother and the octave context fresh from the settled pitch.
                holdRemaining = holdOffFrames
                octaveGuard.reset()
                resolver.reset()
            }
            var framed = false
            assembler.push(chunk) { frame ->
                framed = true
                val raw = detector.detect(frame)
                // Frames that are not clearly above the room's noise floor are treated as silence: a phone mic's
                // low-frequency hum otherwise reads as a very flat low E once the string has faded.
                val gated = if (raw != null && noiseGate.passes(raw.rms)) raw else null
                if (holdRemaining > 0) {
                    holdRemaining--
                    if (holdRemaining == 0) {
                        smoother.reset()
                        octaveGuard.reset()
                    }
                    if (BuildConfig.DEBUG) {
                        Log.v(FRAMES_TAG, "hold raw=%.2f conf=%.2f rms=%.4f".format(raw?.frequencyHz ?: 0.0, raw?.confidence ?: 0.0, level))
                    }
                    _state.update { it.copy(level = level) }
                    return@push
                }
                // A ringing string cannot jump an octave without an attack: fold octave errors onto the note.
                val estimate = octaveGuard.apply(gated)
                val pitchHz = smoother.push(estimate)
                if (pitchHz == null) {
                    silentFrames++
                    if (silentFrames == silenceFramesToReset) {
                        resolver.reset()
                        octaveGuard.reset()
                    }
                } else {
                    silentFrames = 0
                }
                if (BuildConfig.DEBUG) {
                    Log.v(
                        FRAMES_TAG,
                        "raw=%.2f conf=%.2f rms=%.4f gate=%.4f pass=%b guarded=%.2f smooth=%.2f".format(
                            raw?.frequencyHz ?: 0.0, raw?.confidence ?: 0.0, raw?.rms ?: level, noiseGate.threshold,
                            gated != null, estimate?.frequencyHz ?: 0.0, pitchHz ?: 0.0,
                        ),
                    )
                }
                publishFrame(pitchHz, estimate?.confidence ?: 0.0, level)
            }
            if (!framed) _state.update { it.copy(level = level) }
        }
    }

    /** Called under [dspLock] with the smoothed pitch of one frame. */
    private fun publishFrame(pitchHz: Double?, confidence: Double, level: Double) {
        val current = tuning.value
        val targets = current?.frequencies(settings.value.a4Hz)
        if (pitchHz == null || pitchHz <= 0.0 || targets == null || targets.isEmpty()) {
            tunedRun = 0
            _state.update { it.copy(pitchHz = null, confidence = confidence, level = level, centsOff = null, inTune = false) }
            return
        }
        val match = matchTarget(pitchHz, targets)
        if (match == null) {
            // Plausibly no string of this tuning (e.g. a hum far below the low E): show it as silence.
            tunedRun = 0
            _state.update { it.copy(pitchHz = null, confidence = confidence, level = level, centsOff = null, inTune = false) }
            return
        }
        val target = match.index
        val cents = match.centsOff
        val inTune = abs(cents) <= settings.value.toleranceCents
        if (inTune && target == tunedRunTarget) {
            tunedRun++
        } else {
            tunedRun = if (inTune) 1 else 0
            tunedRunTarget = target
        }
        val markTuned = tunedRun >= framesToMarkTuned
        if (BuildConfig.DEBUG) {
            Log.v(
                FRAMES_TAG,
                "target=%d (%.2f Hz) fold=%d cents=%+.1f inTune=%b run=%d".format(target, targets[target], match.fold, cents, inTune, tunedRun),
            )
        }
        _state.update {
            it.copy(
                pitchHz = pitchHz,
                confidence = confidence,
                level = level,
                targetIndex = target,
                centsOff = cents,
                inTune = inTune,
                tunedStrings = if (markTuned) it.tunedStrings + target else it.tunedStrings,
            )
        }
    }

    /**
     * AUTO: the resolver picks the string (overtones folded, implausible pitches rejected). Manual: the pinned string,
     * still folding overtones so a decaying string keeps reading correctly.
     */
    private fun matchTarget(pitchHz: Double, targets: DoubleArray): TargetMatch? {
        val pinned = pinnedString ?: return resolver.resolveMatch(pitchHz, targets)
        val index = pinned.coerceIn(0, targets.lastIndex)
        val fold = Harmonics.bestFold(pitchHz, targets[index])
        return TargetMatch(index, fold, Harmonics.cents(pitchHz, targets[index], fold))
    }

    private fun onTuningChanged(next: Tuning) {
        val previous = lastTuning
        lastTuning = next
        if (previous != null && previous.id == next.id && previous.strings == next.strings) return
        Log.d(TAG, "Tuning: ${next.name} ${next.strings}")
        synchronized(dspLock) {
            resolver.reset()
            tunedRun = 0
            _state.update {
                it.copy(
                    tunedStrings = emptySet(),
                    targetIndex = if (it.autoMode) null else it.targetIndex,
                    centsOff = null,
                    inTune = false,
                )
            }
        }
    }

    /** Must be called under [dspLock]. */
    private fun resetDsp() {
        assembler.reset()
        smoother.reset()
        noiseGate.reset()
        onsetDetector.reset()
        octaveGuard.reset()
        resolver.reset()
        holdRemaining = 0
        silentFrames = 0
        tunedRun = 0
        tunedRunTarget = -1
    }

    private fun markStopped() {
        _state.update {
            it.copy(running = false, pitchHz = null, confidence = 0.0, level = 0.0, centsOff = null, inTune = false)
        }
    }

    private fun clearReadings() {
        _state.update { it.copy(pitchHz = null, confidence = 0.0, level = 0.0, centsOff = null, inTune = false) }
    }

    companion object {
        const val TAG = "TunerEngine"
        /** Per-frame verbose log, debug builds only: `adb logcat -s TunerFrames:V`. */
        const val FRAMES_TAG = "TunerFrames"

        private const val CAPTURE_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 400L

        /** The input to capture from, or null when nothing usable is connected. */
        internal fun chooseInput(
            devices: List<AudioInputDevice>,
            manual: AudioInputDevice?,
            settings: TunerSettings,
        ): AudioInputDevice? {
            if (devices.isEmpty()) return null
            if (manual != null) devices.firstOrNull { it.key == manual.key }?.let { return it }
            val usb = devices.firstOrNull { it.kind == InputKind.USB }
            val builtin = devices.firstOrNull { it.kind == InputKind.BUILTIN_MIC }
            val byPolicy = when (settings.inputPolicy) {
                InputPolicy.PREFER_USB -> usb ?: builtin
                InputPolicy.BUILTIN_MIC -> builtin ?: usb
                InputPolicy.SPECIFIC_DEVICE ->
                    devices.firstOrNull { it.key == settings.preferredInputKey } ?: usb ?: builtin
            }
            return byPolicy ?: devices.first()
        }

        private fun rms(chunk: FloatArray): Double {
            if (chunk.isEmpty()) return 0.0
            var sum = 0.0
            for (x in chunk) sum += x.toDouble() * x
            return sqrt(sum / chunk.size).coerceIn(0.0, 1.0)
        }
    }
}
