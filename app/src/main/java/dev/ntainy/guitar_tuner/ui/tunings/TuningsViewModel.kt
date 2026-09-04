package dev.ntainy.guitar_tuner.ui.tunings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.dsp.NoteMath
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the tunings list. [noteLabels] are low string first, already spelled in the user's notation. */
data class TuningRowUi(
    val id: String,
    val name: String,
    val subtitle: String,
    val noteLabels: List<String>,
    val isSelected: Boolean,
    val isCustom: Boolean,
)

/** A group header plus its rows. "My tunings" is present even when [rows] is empty (the screen shows a hint). */
data class TuningSectionUi(
    val group: TuningGroup,
    val rows: List<TuningRowUi>,
) {
    val label: String get() = group.label
}

data class TuningsUiState(
    val sections: List<TuningSectionUi> = emptyList(),
    val headstockLayout: HeadstockLayout = HeadstockLayout.THREE_PLUS_THREE,
) {
    /** False until both repositories have emitted; afterwards "My tunings" is always the first section. */
    val isLoaded: Boolean get() = sections.isNotEmpty()
}

/** One-shot feedback the screen turns into snackbars. */
sealed interface TuningsMessage {
    data object Copied : TuningsMessage

    /** [wasActive] lets Undo restore the selection as well as the tuning. */
    data class Deleted(val tuning: Tuning, val wasActive: Boolean) : TuningsMessage
}

class TuningsViewModel(
    private val tunings: TuningsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val messageChannel = Channel<TuningsMessage>(Channel.BUFFERED)

    val messages: Flow<TuningsMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<TuningsUiState> =
        combine(tunings.tunings, settings.settings) { all, prefs ->
            TuningsUiState(
                sections = buildSections(all, prefs.activeTuningId, prefs.notation),
                headstockLayout = prefs.headstockLayout,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TuningsUiState())

    fun select(id: String) {
        viewModelScope.launch { settings.update { it.copy(activeTuningId = id) } }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            tunings.duplicate(id)
            messageChannel.send(TuningsMessage.Copied)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val tuning = tunings.tuning(id).first() ?: return@launch
            if (tuning.isPreset) return@launch
            val wasActive = settings.settings.first().activeTuningId == id
            tunings.delete(id)
            if (wasActive) settings.update { it.copy(activeTuningId = PresetIds.STANDARD) }
            messageChannel.send(TuningsMessage.Deleted(tuning, wasActive))
        }
    }

    /** Undo for [TuningsMessage.Deleted]: puts the tuning back and re-selects it if it was active. */
    fun undoDelete(deleted: TuningsMessage.Deleted) {
        viewModelScope.launch {
            tunings.upsert(deleted.tuning)
            if (deleted.wasActive) settings.update { it.copy(activeTuningId = deleted.tuning.id) }
        }
    }

    fun setHeadstockLayout(layout: HeadstockLayout) {
        viewModelScope.launch { settings.update { it.copy(headstockLayout = layout) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Groups tunings in [TuningGroup] order; "My tunings" always comes first, other groups only when non-empty. */
internal fun buildSections(all: List<Tuning>, activeId: String, notation: Notation): List<TuningSectionUi> {
    val byGroup = all.groupBy { if (it.isPreset) it.group else TuningGroup.MINE }
    return TuningGroup.entries.mapNotNull { group ->
        val rows = byGroup[group].orEmpty().map { it.toRow(activeId, notation) }
        if (group == TuningGroup.MINE || rows.isNotEmpty()) TuningSectionUi(group, rows) else null
    }
}

private fun Tuning.toRow(activeId: String, notation: Notation): TuningRowUi {
    val letters = strings.joinToString(" ") { NoteMath.letter(it, notation) }
    return TuningRowUi(
        id = id,
        name = name,
        subtitle = if (isPreset) subtitle.ifBlank { letters } else letters,
        noteLabels = strings.map { NoteMath.name(it, notation) },
        isSelected = id == activeId,
        isCustom = !isPreset,
    )
}
