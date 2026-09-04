package dev.ntainy.guitar_tuner.ui.editor

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import dev.ntainy.guitar_tuner.ui.tunings.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class TuningEditorViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val standard = InMemoryTuningsRepository.STANDARD
    private val dropD = InMemoryTuningsRepository.DROP_D
    private val mine = Tuning(
        id = "custom_1",
        name = "Mine",
        subtitle = "C G C G C E",
        strings = listOf(36, 43, 48, 55, 60, 64),
        createdAt = 123L,
    )

    private fun viewModel(
        tuningId: String?,
        tunings: List<Tuning> = listOf(standard, dropD, mine),
        settings: TunerSettings = TunerSettings(),
    ): Pair<TuningEditorViewModel, InMemoryTuningsRepository> {
        val tuningsRepo = InMemoryTuningsRepository(tunings)
        return TuningEditorViewModel(tuningsRepo, InMemorySettingsRepository(settings), tuningId) to tuningsRepo
    }

    private suspend fun ReceiveTurbine<TuningEditorUiState>.awaitLoaded(): TuningEditorUiState {
        var state = awaitItem()
        while (!state.isLoaded) state = awaitItem()
        return state
    }

    @Test
    fun newTuningStartsFromStandard() = runTest {
        val (vm, _) = viewModel(tuningId = null)
        vm.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.isNew)
            assertEquals("New tuning", state.name)
            assertEquals(standard.strings, state.notes)
            assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), state.noteLabels)
            assertTrue(state.errors.isEmpty())
            assertTrue(state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presetIdStartsANewTuningPrefilledFromThatPreset() = runTest {
        val (vm, _) = viewModel(tuningId = dropD.id)
        vm.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.isNew)
            assertEquals("New tuning", state.name)
            assertEquals(dropD.strings, state.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noteLabelsHonourNotation() = runTest {
        val (vm, _) = viewModel(tuningId = null, settings = TunerSettings(notation = Notation.FLATS))
        vm.uiState.test {
            awaitLoaded()
            vm.setNote(0, 39)
            assertEquals("E♭2", awaitItem().noteLabels[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun nudgeMovesOneStringAndClampsAtTheRange() = runTest {
        val (vm, _) = viewModel(tuningId = null)
        vm.uiState.test {
            awaitLoaded()
            vm.nudge(0, -2)
            assertEquals(listOf(38, 45, 50, 55, 59, 64), awaitItem().notes)
            vm.nudge(5, +1)
            assertEquals(65, awaitItem().notes[5])

            vm.setNote(5, MAX_STRING_MIDI)
            assertEquals(MAX_STRING_MIDI, awaitItem().notes[5])
            vm.nudge(5, +1)
            expectNoEvents()

            vm.setNote(0, MIN_STRING_MIDI)
            assertEquals(MIN_STRING_MIDI, awaitItem().notes[0])
            vm.nudge(0, -1)
            expectNoEvents()

            vm.setNote(2, 999)
            assertEquals(MAX_STRING_MIDI, awaitItem().notes[2])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shiftAllMovesEveryStringOrRefusesWhenOneWouldLeaveTheRange() = runTest {
        val (vm, _) = viewModel(tuningId = null)
        vm.uiState.test {
            val initial = awaitLoaded()
            assertTrue(initial.canShiftDown)
            assertTrue(initial.canShiftUp)

            vm.shiftAll(-1)
            assertEquals(listOf(39, 44, 49, 54, 58, 63), awaitItem().notes)
            vm.shiftAll(+2)
            assertEquals(listOf(41, 46, 51, 56, 60, 65), awaitItem().notes)

            vm.setNote(5, MAX_STRING_MIDI)
            val pinned = awaitItem()
            assertFalse(pinned.canShiftUp)
            assertTrue(pinned.canShiftDown)
            vm.shiftAll(+1)
            expectNoEvents()
            assertEquals(listOf(41, 46, 51, 56, 60, MAX_STRING_MIDI), pinned.notes)

            vm.setNote(0, MIN_STRING_MIDI)
            assertFalse(awaitItem().canShiftDown)
            vm.shiftAll(-1)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun blankNameIsAValidationErrorThatBlocksSave() = runTest {
        val (vm, tuningsRepo) = viewModel(tuningId = null, tunings = listOf(standard))
        vm.uiState.test {
            awaitLoaded()
            vm.setName("   ")
            val state = awaitItem()
            assertEquals(listOf("Name is empty"), state.errors)
            assertFalse(state.canSave)

            vm.save()
            assertTrue(tuningsRepo.tunings.first().none { !it.isPreset })

            vm.setName("Named")
            val fixed = awaitItem()
            assertTrue(fixed.errors.isEmpty())
            assertTrue(fixed.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveUpsertsAMineTuningWithAFreshIdAndReportsSaved() = runTest {
        val (vm, tuningsRepo) = viewModel(tuningId = null)
        vm.uiState.test {
            awaitLoaded()
            vm.setName("Drop A")
            awaitItem()
            vm.setNote(0, 33)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.save()
            val saved = assertIs<TuningEditorEvent.Saved>(awaitItem())
            assertTrue(saved.id.startsWith("custom_"), saved.id)
            assertTrue(saved.id.removePrefix("custom_").length >= 32)

            val stored = assertNotNull(tuningsRepo.tuning(saved.id).first())
            assertEquals("Drop A", stored.name)
            assertEquals(listOf(33, 45, 50, 55, 59, 64), stored.strings)
            assertEquals(TuningGroup.MINE, stored.group)
            assertFalse(stored.isPreset)
            assertTrue(stored.createdAt > 0L)
            assertEquals("A A D G B E", stored.subtitle)
            assertEquals(4, tuningsRepo.tunings.first().size)
        }
    }

    @Test
    fun saveTrimsTheName() = runTest {
        val (vm, tuningsRepo) = viewModel(tuningId = null, tunings = listOf(standard))
        vm.uiState.test {
            awaitLoaded()
            vm.setName("  Spaced  ")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.save()
            val saved = assertIs<TuningEditorEvent.Saved>(awaitItem())
            assertEquals("Spaced", tuningsRepo.tuning(saved.id).first()?.name)
        }
    }

    @Test
    fun editingLoadsTheCustomTuningAndSaveKeepsItsIdentity() = runTest {
        val (vm, tuningsRepo) = viewModel(tuningId = mine.id)
        vm.uiState.test {
            val state = awaitLoaded()
            assertFalse(state.isNew)
            assertEquals("Mine", state.name)
            assertEquals(mine.strings, state.notes)
            assertTrue(state.canSave)

            vm.setName("Open C, edited")
            awaitItem()
            vm.nudge(5, -2)
            assertEquals(62, awaitItem().notes[5])
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.save()
            val saved = assertIs<TuningEditorEvent.Saved>(awaitItem())
            assertEquals(mine.id, saved.id)
        }
        val stored = assertNotNull(tuningsRepo.tuning(mine.id).first())
        assertEquals("Open C, edited", stored.name)
        assertEquals(listOf(36, 43, 48, 55, 60, 62), stored.strings)
        assertEquals(mine.createdAt, stored.createdAt)
        assertEquals(TuningGroup.MINE, stored.group)
        assertEquals(1, tuningsRepo.tunings.first().count { !it.isPreset })
    }

    @Test
    fun unknownIdFallsBackToANewTuningFromStandard() = runTest {
        val (vm, _) = viewModel(tuningId = "custom_missing")
        vm.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.isNew)
            assertEquals(standard.strings, state.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newTuningWithoutAStandardPresetInTheRepositoryStillPrefillsStandardNotes() = runTest {
        val (vm, _) = viewModel(tuningId = null, tunings = emptyList())
        vm.uiState.test {
            assertEquals(standard.strings, awaitLoaded().notes)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(PresetIds.STANDARD, standard.id)
    }
}
