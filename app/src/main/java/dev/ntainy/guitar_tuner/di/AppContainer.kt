package dev.ntainy.guitar_tuner.di

import android.content.Context
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.fakes.FakeTunerEngine
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Hand-rolled dependency graph. One instance per process, owned by [dev.ntainy.guitar_tuner.TunerApplication].
 *
 * M0 wires the in-memory fakes so the skeleton runs; M5 swaps in the Wave 1 implementations
 * (DataStore repositories, AudioRecord-backed engine). Screens receive the container and build their
 * own ViewModels from it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class AppContainer(val appContext: Context) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    open val settingsRepository: SettingsRepository by lazy { InMemorySettingsRepository() }

    open val tuningsRepository: TuningsRepository by lazy { InMemoryTuningsRepository() }

    open val tunerEngine: TunerEngine by lazy { FakeTunerEngine() }

    /** The tuning selected in settings, falling back to Standard if the id no longer exists. */
    val activeTuning: Flow<Tuning> by lazy {
        settingsRepository.settings
            .map { it.activeTuningId }
            .distinctUntilChanged()
            .flatMapLatest { id -> tuningsRepository.tuning(id) }
            .map { it ?: InMemoryTuningsRepository.STANDARD }
    }
}
