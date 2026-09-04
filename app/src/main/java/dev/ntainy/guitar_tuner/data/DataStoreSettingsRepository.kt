package dev.ntainy.guitar_tuner.data

import androidx.datastore.core.DataStore
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by a typed DataStore of [TunerSettings] (`settings.json`).
 *
 * Numeric fields are clamped to the ranges the UI allows on every write and again on read, so a hand-edited or
 * pre-clamping file can never push `a4Hz` or `toleranceCents` out of range. A corrupt or unreadable file reads as
 * default settings.
 *
 * @param dataStore the store created by [createSettingsDataStore].
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<TunerSettings>,
) : SettingsRepository {

    override val settings: Flow<TunerSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(TunerSettings()) else throw e }
        .map { it.sanitized() }
        .distinctUntilChanged()

    override suspend fun update(transform: (TunerSettings) -> TunerSettings) {
        dataStore.updateData { current -> transform(current).sanitized() }
    }
}

/**
 * Clamps `a4Hz` to [TunerSettings.MIN_A4_HZ]..[TunerSettings.MAX_A4_HZ] and `toleranceCents` to
 * [TunerSettings.MIN_TOLERANCE_CENTS]..[TunerSettings.MAX_TOLERANCE_CENTS]; a NaN falls back to the default value.
 * Every `sensitivityDb` entry is clamped to [TunerSettings.MIN_SENSITIVITY_DB]..[TunerSettings.MAX_SENSITIVITY_DB];
 * NaN and 0 dB entries are dropped (0 dB is what a missing entry means).
 */
internal fun TunerSettings.sanitized(): TunerSettings = copy(
    a4Hz = a4Hz.clampOr(TunerSettings.MIN_A4_HZ, TunerSettings.MAX_A4_HZ, DEFAULT_SETTINGS.a4Hz),
    toleranceCents = toleranceCents.clampOr(
        TunerSettings.MIN_TOLERANCE_CENTS,
        TunerSettings.MAX_TOLERANCE_CENTS,
        DEFAULT_SETTINGS.toleranceCents,
    ),
    sensitivityDb = sensitivityDb.mapNotNull { (key, db) ->
        if (db.isNaN() || db == 0.0) {
            null
        } else {
            key to db.coerceIn(TunerSettings.MIN_SENSITIVITY_DB, TunerSettings.MAX_SENSITIVITY_DB)
        }
    }.toMap(),
)

private val DEFAULT_SETTINGS = TunerSettings()

private fun Double.clampOr(min: Double, max: Double, fallback: Double): Double =
    if (isNaN()) fallback else coerceIn(min, max)
