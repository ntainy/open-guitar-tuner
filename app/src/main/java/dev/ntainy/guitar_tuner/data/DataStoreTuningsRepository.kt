package dev.ntainy.guitar_tuner.data

import androidx.datastore.core.DataStore
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.data.presets.PresetTunings
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [TuningsRepository] backed by a typed DataStore of [TuningsFile] (the user's custom tunings) plus the read-only
 * [presets] catalog.
 *
 * - [tunings] lists custom tunings oldest first (ties broken by id), then presets in catalog order.
 * - Custom tunings are always stored with [TuningGroup.MINE], `isPreset = false` and a non-zero [Tuning.createdAt].
 * - Presets are never written. A corrupt or unreadable file reads as "no custom tunings", so presets stay available.
 *
 * @param dataStore the store created by [createTuningsDataStore].
 * @param presets the catalog appended after custom tunings; defaults to [PresetTunings.all].
 * @param clock source of [Tuning.createdAt] timestamps in epoch millis.
 * @param idFactory generates ids for new custom tunings; must never produce ids starting with [PresetIds.PREFIX].
 */
class DataStoreTuningsRepository(
    private val dataStore: DataStore<TuningsFile>,
    private val presets: List<Tuning> = PresetTunings.all,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { "custom_" + UUID.randomUUID() },
) : TuningsRepository {

    private val customTunings: Flow<List<Tuning>> = dataStore.data
        .map { file -> file.tunings }
        .catch { e -> if (e is IOException) emit(emptyList()) else throw e }

    override val tunings: Flow<List<Tuning>> = customTunings
        .map { custom -> custom.sortedWith(CUSTOM_ORDER) + presets }
        .distinctUntilChanged()

    override fun tuning(id: String): Flow<Tuning?> =
        tunings.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    /**
     * Inserts or replaces a custom tuning.
     *
     * A zero [Tuning.createdAt] is filled in: an existing entry with the same id keeps its original timestamp (so
     * editing does not reorder the list), a new entry gets [clock].
     */
    override suspend fun upsert(tuning: Tuning) {
        require(!tuning.isPresetLike) { "Presets are read-only" }
        val errors = tuning.validationErrors()
        require(errors.isEmpty()) { errors.joinToString() }
        dataStore.updateData { file ->
            val existing = file.tunings.firstOrNull { it.id == tuning.id }
            val stored = tuning.copy(
                isPreset = false,
                group = TuningGroup.MINE,
                createdAt = when {
                    tuning.createdAt != 0L -> tuning.createdAt
                    existing != null -> existing.createdAt
                    else -> clock()
                },
            )
            file.withTunings(file.tunings.filterNot { it.id == tuning.id } + stored)
        }
    }

    override suspend fun delete(id: String) {
        if (id.startsWith(PresetIds.PREFIX)) return
        dataStore.updateData { file ->
            if (file.tunings.none { it.id == id }) file else file.withTunings(file.tunings.filterNot { it.id == id })
        }
    }

    /**
     * Copies a preset or custom tuning into the user's list.
     *
     * Without [newName] the copy is called "<name> copy", or "<name> copy 2", "<name> copy 3", ... when that name is
     * already taken by any tuning.
     *
     * @throws NoSuchElementException when no tuning has the given [id].
     */
    override suspend fun duplicate(id: String, newName: String?): Tuning {
        val newId = idFactory()
        val createdAt = clock()
        val updated = dataStore.updateData { file ->
            val source = presets.firstOrNull { it.id == id }
                ?: file.tunings.firstOrNull { it.id == id }
                ?: throw NoSuchElementException("No tuning with id $id")
            val taken = buildSet {
                presets.mapTo(this) { it.name }
                file.tunings.mapTo(this) { it.name }
            }
            val copy = source.copy(
                id = newId,
                name = newName?.takeIf { it.isNotBlank() } ?: copyName(source.name, taken),
                isPreset = false,
                group = TuningGroup.MINE,
                createdAt = createdAt,
            )
            file.withTunings(file.tunings + copy)
        }
        return updated.tunings.first { it.id == newId }
    }

    private val Tuning.isPresetLike: Boolean
        get() = isPreset || id.startsWith(PresetIds.PREFIX)

    private fun TuningsFile.withTunings(tunings: List<Tuning>): TuningsFile =
        copy(version = TuningsFile.CURRENT_VERSION, tunings = tunings)

    private fun copyName(base: String, taken: Set<String>): String {
        val plain = "$base copy"
        if (plain !in taken) return plain
        return generateSequence(2) { it + 1 }.map { "$plain $it" }.first { it !in taken }
    }

    private companion object {
        val CUSTOM_ORDER: Comparator<Tuning> = compareBy({ it.createdAt }, { it.id })
    }
}
