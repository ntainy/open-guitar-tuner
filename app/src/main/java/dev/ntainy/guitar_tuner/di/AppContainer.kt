package dev.ntainy.guitar_tuner.di

import android.content.Context
import dev.ntainy.guitar_tuner.audio.AndroidAudioInputMonitor
import dev.ntainy.guitar_tuner.audio.AudioInputMonitor
import dev.ntainy.guitar_tuner.audio.AudioRecordSource
import dev.ntainy.guitar_tuner.audio.AudioSource
import dev.ntainy.guitar_tuner.audio.AudioTrackReferenceTonePlayer
import dev.ntainy.guitar_tuner.audio.AudioTunerEngine
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.ReferenceTonePlayer
import dev.ntainy.guitar_tuner.audio.SyntheticToneSource
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.data.DataStoreSettingsRepository
import dev.ntainy.guitar_tuner.data.DataStoreTuningsRepository
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.createSettingsDataStore
import dev.ntainy.guitar_tuner.data.createTuningsDataStore
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.presets.PresetTunings
import dev.ntainy.guitar_tuner.dsp.HysteresisTargetResolver
import dev.ntainy.guitar_tuner.dsp.MedianEmaSmoother
import dev.ntainy.guitar_tuner.dsp.RingBufferFrameAssembler
import dev.ntainy.guitar_tuner.dsp.YinPitchDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Hand-rolled dependency graph. One instance per process, owned by [dev.ntainy.guitar_tuner.TunerApplication].
 *
 * Everything is a lazy singleton: DataStore refuses a second instance on the same file, and the engine keeps an
 * `AudioDeviceCallback` registered for the life of the process. Screens receive the container and build their own
 * ViewModels from it. Previews and tests subclass this and override the repositories/engine with the fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class AppContainer(val appContext: Context) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    open val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(createSettingsDataStore(appContext, json, appScope))
    }

    open val tuningsRepository: TuningsRepository by lazy {
        DataStoreTuningsRepository(createTuningsDataStore(appContext, json, appScope))
    }

    /** Frequency of the debug "Test tone" input; the input sheet drives it, the synthetic source reads it. */
    val testToneFrequencyHz: MutableStateFlow<Double> = MutableStateFlow(110.0)

    open val inputMonitor: AudioInputMonitor by lazy { AndroidAudioInputMonitor(appContext) }

    open val tunerEngine: TunerEngine by lazy {
        AudioTunerEngine(
            scope = appScope,
            inputMonitor = inputMonitor,
            sourceFactory = ::createSource,
            detector = YinPitchDetector(),
            smoother = MedianEmaSmoother(medianWindow = 5, alpha = 0.3),
            resolver = HysteresisTargetResolver(),
            assembler = RingBufferFrameAssembler(),
            tuningFlow = activeTuning,
            settingsFlow = settingsRepository.settings,
        )
    }

    /** Reference tones for long-pressing a string. Notes run on [appScope], off the main thread. */
    open val referenceTonePlayer: ReferenceTonePlayer by lazy {
        AudioTrackReferenceTonePlayer(appContext = appContext, scope = appScope)
    }

    /** The tuning selected in settings, falling back to Standard if the id no longer exists. */
    val activeTuning: Flow<Tuning> by lazy {
        settingsRepository.settings
            .map { it.activeTuningId }
            .distinctUntilChanged()
            .flatMapLatest { id -> tuningsRepository.tuning(id) }
            .map { it ?: standardTuning }
    }

    private val standardTuning: Tuning by lazy { checkNotNull(PresetTunings.byId(PresetIds.STANDARD)) }

    private fun createSource(device: dev.ntainy.guitar_tuner.audio.AudioInputDevice): AudioSource =
        if (device.kind == InputKind.TEST_TONE) SyntheticToneSource(testToneFrequencyHz) else AudioRecordSource(appContext, device)
}
