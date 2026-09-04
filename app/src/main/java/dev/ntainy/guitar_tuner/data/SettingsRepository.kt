package dev.ntainy.guitar_tuner.data

import dev.ntainy.guitar_tuner.data.model.TunerSettings
import kotlinx.coroutines.flow.Flow

/** Persisted user settings. Wave 1 implementation: `DataStoreSettingsRepository`. */
interface SettingsRepository {
    val settings: Flow<TunerSettings>

    suspend fun update(transform: (TunerSettings) -> TunerSettings)
}
