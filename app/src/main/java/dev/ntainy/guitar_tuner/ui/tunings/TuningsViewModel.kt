package dev.ntainy.guitar_tuner.ui.tunings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.dsp.NoteMath
import kotlin.math.abs
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
    /** The chromatic entry: it has no strings to show, so the row carries no note chips. */
    val isChromatic: Boolean = false,
)

/** A group header plus its rows. "My tunings" is present even when [rows] is empty (the screen shows a hint). */
data class TuningSectionUi(
    val group: TuningGroup,
    val rows: List<TuningRowUi>,
) {
    val label: String get() = group.label
}

/**
 * The tunings list. [chromatic] is the mode row that sits above every section header — it is not a tuning,
 * so it never appears inside [sections].
 */
data class TuningsUiState(
    val chromatic: TuningRowUi? = null,
    val sections: List<TuningSectionUi> = emptyList(),
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
            buildTuningsState(all, prefs.activeTuningId, prefs.notation)
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Standard tuning, low string first (E2 A2 D3 G3 B3 E4): what a custom tuning's subtitle is measured against. */
private val STANDARD_STRINGS: List<Int> = listOf(40, 45, 50, 55, 59, 64)

/** Guitar string numbers by list index: index 0 (the low string) is the 6th. */
private val STRING_ORDINALS: List<String> = listOf("6th", "5th", "4th", "3rd", "2nd", "1st")

/** The whole list state: the chromatic mode row (when the catalog has one) plus the grouped tunings. */
internal fun buildTuningsState(all: List<Tuning>, activeId: String, notation: Notation): TuningsUiState =
    TuningsUiState(
        chromatic = all.firstOrNull { it.isChromatic }?.toRow(activeId, notation),
        sections = buildSections(all, activeId, notation),
    )

/**
 * Groups tunings in [TuningGroup] order; "My tunings" always comes first, other groups only when non-empty.
 * The chromatic entry is a mode rather than a tuning and is left out — see [TuningsUiState.chromatic].
 */
internal fun buildSections(all: List<Tuning>, activeId: String, notation: Notation): List<TuningSectionUi> {
    val byGroup = all.filterNot { it.isChromatic }.groupBy { if (it.isPreset) it.group else TuningGroup.MINE }
    return TuningGroup.entries.mapNotNull { group ->
        val rows = byGroup[group].orEmpty().map { it.toRow(activeId, notation) }
        if (group == TuningGroup.MINE || rows.isNotEmpty()) TuningSectionUi(group, rows) else null
    }
}

/**
 * How a custom tuning differs from Standard, per string, as a compact line: "6th −2 · 3rd −1". Empty when it
 * is Standard. The chips already spell the notes, so the subtitle says what was changed instead of repeating them.
 */
internal fun distanceFromStandard(strings: List<Int>): String =
    strings.zip(STANDARD_STRINGS)
        .mapIndexedNotNull { index, (midi, standard) ->
            val delta = midi - standard
            if (delta == 0) null else "${STRING_ORDINALS[index]} ${if (delta < 0) "−" else "+"}${abs(delta)}"
        }
        .joinToString(" · ")

private fun Tuning.toRow(activeId: String, notation: Notation): TuningRowUi = TuningRowUi(
    id = id,
    name = name,
    subtitle = when {
        isChromatic -> subtitle
        isPreset -> subtitle.ifBlank { strings.joinToString(" ") { NoteMath.letter(it, notation) } }
        else -> distanceFromStandard(strings)
    },
    noteLabels = if (isChromatic) emptyList() else strings.map { NoteMath.name(it, notation) },
    isSelected = id == activeId,
    isCustom = !isPreset,
    isChromatic = isChromatic,
)
