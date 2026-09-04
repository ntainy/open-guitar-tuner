package dev.ntainy.guitar_tuner.fakes

import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory settings for previews, tests and the M0 skeleton. */
class InMemorySettingsRepository(initial: TunerSettings = TunerSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<TunerSettings> = state.asStateFlow()

    override suspend fun update(transform: (TunerSettings) -> TunerSettings) {
        state.update(transform)
    }
}
