package dev.ntainy.guitar_tuner.fakes

import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory repository for previews, tests and the M0 skeleton. Wave 1 adds the DataStore-backed one. */
class InMemoryTuningsRepository(initial: List<Tuning> = listOf(STANDARD, DROP_D)) : TuningsRepository {
    private val state = MutableStateFlow(initial)

    override val tunings: Flow<List<Tuning>> =
        state.map { list -> list.filter { !it.isPreset }.sortedBy { it.createdAt } + list.filter { it.isPreset } }

    override fun tuning(id: String): Flow<Tuning?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun upsert(tuning: Tuning) {
        require(!tuning.isPreset) { "Presets are read-only" }
        require(tuning.isValid) { tuning.validationErrors().joinToString() }
        state.update { list -> list.filter { it.id != tuning.id } + tuning }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id && !it.isPreset } }
    }

    override suspend fun duplicate(id: String, newName: String?): Tuning {
        val source = state.value.first { it.id == id }
        val copy = source.copy(
            id = "custom_" + UUID.randomUUID(),
            name = newName ?: "${source.name} copy",
            isPreset = false,
            group = TuningGroup.MINE,
            createdAt = System.currentTimeMillis(),
        )
        state.update { it + copy }
        return copy
    }

    companion object {
        val STANDARD = Tuning(
            id = PresetIds.STANDARD,
            name = "Standard",
            subtitle = "E A D G B E",
            strings = listOf(40, 45, 50, 55, 59, 64),
            isPreset = true,
            group = TuningGroup.STANDARD,
        )
        val DROP_D = Tuning(
            id = "preset_drop_d",
            name = "Drop D",
            subtitle = "D A D G B E",
            strings = listOf(38, 45, 50, 55, 59, 64),
            isPreset = true,
            group = TuningGroup.POWER,
        )
    }
}
