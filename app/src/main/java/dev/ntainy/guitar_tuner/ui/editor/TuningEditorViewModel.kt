package dev.ntainy.guitar_tuner.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Labels for the six rows of the editor, index 0 = low string. */
val STRING_LABELS: List<String> = listOf("6th (low E)", "5th", "4th", "3rd", "2nd", "1st (high E)")

/** Everything the editor draws. [notes] and [noteLabels] are low string first. */
data class TuningEditorUiState(
    val name: String = "",
    val notes: List<Int> = InMemoryTuningsRepository.STANDARD.strings,
    val notation: Notation = Notation.SHARPS,
    val errors: List<String> = emptyList(),
    val isNew: Boolean = true,
    val isLoaded: Boolean = false,
) {
    val noteLabels: List<String> get() = notes.map { NoteMath.name(it, notation) }
    val canSave: Boolean get() = isLoaded && errors.isEmpty()
    val canShiftDown: Boolean get() = notes.all { it - 1 >= MIN_STRING_MIDI }
    val canShiftUp: Boolean get() = notes.all { it + 1 <= MAX_STRING_MIDI }
}

sealed interface TuningEditorEvent {
    data class Saved(val id: String) : TuningEditorEvent
}

/**
 * Edits the custom tuning [tuningId], or starts a new one when the id is null or names a preset.
 * A new tuning is prefilled from Standard (or from the named preset) and called "New tuning".
 */
class TuningEditorViewModel(
    private val tunings: TuningsRepository,
    private val settings: SettingsRepository,
    private val tuningId: String?,
) : ViewModel() {
    private data class Draft(
        val id: String?,
        val name: String,
        val notes: List<Int>,
        val createdAt: Long,
    ) {
        val isNew: Boolean get() = id == null
    }

    private val draft = MutableStateFlow<Draft?>(null)
    private val eventChannel = Channel<TuningEditorEvent>(Channel.BUFFERED)

    val events: Flow<TuningEditorEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<TuningEditorUiState> =
        combine(draft.filterNotNull(), settings.settings) { d, prefs ->
            TuningEditorUiState(
                name = d.name,
                notes = d.notes,
                notation = prefs.notation,
                errors = d.toTuning(prefs.notation).validationErrors(),
                isNew = d.isNew,
                isLoaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TuningEditorUiState())

    init {
        viewModelScope.launch { draft.value = load() }
    }

    private suspend fun load(): Draft {
        val existing = tuningId?.let { tunings.tuning(it).first() }
        if (existing != null && !existing.isPreset) {
            return Draft(id = existing.id, name = existing.name, notes = existing.strings, createdAt = existing.createdAt)
        }
        val template = existing
            ?: tunings.tuning(PresetIds.STANDARD).first()
            ?: InMemoryTuningsRepository.STANDARD
        return Draft(id = null, name = NEW_NAME, notes = template.strings, createdAt = 0L)
    }

    fun setName(name: String) = draft.update { it?.copy(name = name) }

    fun setNote(index: Int, midi: Int) = draft.update { d ->
        d?.copy(notes = d.notes.mapIndexed { i, n -> if (i == index) midi.coerceIn(MIN_STRING_MIDI, MAX_STRING_MIDI) else n })
    }

    /** Moves one string by [delta] semitones, clamped to the editor's range. */
    fun nudge(index: Int, delta: Int) = draft.update { d ->
        d?.copy(notes = d.notes.mapIndexed { i, n -> if (i == index) (n + delta).coerceIn(MIN_STRING_MIDI, MAX_STRING_MIDI) else n })
    }

    /** Moves every string by [delta] semitones; a no-op if any string would leave the range. */
    fun shiftAll(delta: Int) = draft.update { d ->
        if (d == null || d.notes.any { it + delta !in MIN_STRING_MIDI..MAX_STRING_MIDI }) d
        else d.copy(notes = d.notes.map { it + delta })
    }

    fun save() {
        val d = draft.value ?: return
        viewModelScope.launch {
            val notation = settings.settings.first().notation
            val tuning = d.toTuning(notation).let { t ->
                t.copy(
                    id = t.id.ifEmpty { "custom_" + UUID.randomUUID() },
                    createdAt = if (t.createdAt == 0L) System.currentTimeMillis() else t.createdAt,
                )
            }
            if (!tuning.isValid) return@launch
            tunings.upsert(tuning)
            draft.update { it?.copy(id = tuning.id, createdAt = tuning.createdAt) }
            eventChannel.send(TuningEditorEvent.Saved(tuning.id))
        }
    }

    private fun Draft.toTuning(notation: Notation): Tuning = Tuning(
        id = id.orEmpty(),
        name = name.trim(),
        subtitle = notes.joinToString(" ") { NoteMath.letter(it, notation) },
        strings = notes,
        isPreset = false,
        group = TuningGroup.MINE,
        createdAt = createdAt,
    )

    private companion object {
        const val NEW_NAME = "New tuning"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
