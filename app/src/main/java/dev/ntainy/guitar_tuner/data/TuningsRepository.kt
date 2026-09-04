package dev.ntainy.guitar_tuner.data

import dev.ntainy.guitar_tuner.data.model.Tuning
import kotlinx.coroutines.flow.Flow

/**
 * All tunings the app knows about: the user's own (unlimited) plus the built-in presets.
 *
 * Wave 1 implementation: `DataStoreTuningsRepository` (typed DataStore, JSON file with a version field).
 */
interface TuningsRepository {
    /** Custom tunings first (newest last), then presets in catalog order. */
    val tunings: Flow<List<Tuning>>

    fun tuning(id: String): Flow<Tuning?>

    /**
     * Insert or replace a custom tuning.
     * @throws IllegalArgumentException when the tuning is a preset or fails [Tuning.validationErrors].
     */
    suspend fun upsert(tuning: Tuning)

    /** Deletes a custom tuning; presets are ignored. */
    suspend fun delete(id: String)

    /** Copies any tuning (preset or custom) into the user's list and returns the new entry. */
    suspend fun duplicate(id: String, newName: String? = null): Tuning
}
