package dev.ntainy.guitar_tuner.ui.tunings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class TuningsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val standard = InMemoryTuningsRepository.STANDARD
    private val dropD = InMemoryTuningsRepository.DROP_D
    private val chromatic = Tuning(
        id = PresetIds.CHROMATIC,
        name = "Chromatic",
        subtitle = "Any note, no strings",
        strings = standard.strings,
        isPreset = true,
        group = TuningGroup.STANDARD,
    )
    private val openG = Tuning(
        id = "preset_open_g",
        name = "Open G",
        subtitle = "D G D G B D",
        strings = listOf(38, 43, 50, 55, 59, 62),
        isPreset = true,
        group = TuningGroup.OPEN,
    )
    private val mine = Tuning(
        id = "custom_1",
        name = "Mine",
        strings = listOf(38, 45, 50, 55, 58, 63),
        createdAt = 10L,
    )

    private fun viewModel(
        tunings: List<Tuning> = listOf(standard, dropD),
        settings: TunerSettings = TunerSettings(),
    ): Triple<TuningsViewModel, InMemoryTuningsRepository, InMemorySettingsRepository> {
        val tuningsRepo = InMemoryTuningsRepository(tunings)
        val settingsRepo = InMemorySettingsRepository(settings)
        return Triple(TuningsViewModel(tuningsRepo, settingsRepo), tuningsRepo, settingsRepo)
    }

    private suspend fun ReceiveTurbine<TuningsUiState>.awaitLoaded(): TuningsUiState {
        var state = awaitItem()
        while (!state.isLoaded) state = awaitItem()
        return state
    }

    @Test
    fun groupsInEnumOrderWithMyTuningsFirst() = runTest {
        val (vm, _, _) = viewModel(tunings = listOf(openG, standard, dropD, mine))
        vm.uiState.test {
            val state = awaitLoaded()
            assertEquals(
                listOf(TuningGroup.MINE, TuningGroup.STANDARD, TuningGroup.POWER, TuningGroup.OPEN),
                state.sections.map { it.group },
            )
            assertEquals(listOf("Mine"), state.sections[0].rows.map { it.name })
            assertTrue(state.sections[0].rows.single().isCustom)
            assertEquals("Open G", state.sections.last().rows.single().name)
            assertFalse(state.sections.last().rows.single().isCustom)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyMyTuningsSectionIsStillFirst() = runTest {
        val (vm, _, _) = viewModel()
        vm.uiState.test {
            val state = awaitLoaded()
            val mineSection = state.sections.first()
            assertEquals(TuningGroup.MINE, mineSection.group)
            assertTrue(mineSection.rows.isEmpty())
            assertEquals("My tunings", mineSection.label)
            assertEquals(listOf(TuningGroup.STANDARD, TuningGroup.POWER), state.sections.drop(1).map { it.group })
            assertTrue(state.sections.drop(1).all { it.rows.isNotEmpty() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chromaticIsItsOwnRowAndNeverASectionMember() = runTest {
        val (vm, _, _) = viewModel(tunings = listOf(chromatic, standard, mine))
        vm.uiState.test {
            val state = awaitLoaded()
            val row = assertNotNull(state.chromatic)
            assertEquals(PresetIds.CHROMATIC, row.id)
            assertEquals("Chromatic", row.name)
            assertEquals("Any note, no strings", row.subtitle)
            assertTrue(row.isChromatic)
            assertTrue(row.noteLabels.isEmpty())
            assertFalse(row.isSelected)
            assertTrue(state.sections.flatMap { it.rows }.none { it.isChromatic })
            val standardSection = state.sections.first { it.group == TuningGroup.STANDARD }
            assertEquals(listOf("Standard"), standardSection.rows.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutAChromaticEntryTheRowIsAbsent() = runTest {
        val (vm, _, _) = viewModel()
        vm.uiState.test {
            assertNull(awaitLoaded().chromatic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingChromaticMarksTheModeRow() = runTest {
        val (vm, _, settingsRepo) = viewModel(tunings = listOf(chromatic, standard))
        vm.uiState.test {
            assertFalse(assertNotNull(awaitLoaded().chromatic).isSelected)
            vm.select(PresetIds.CHROMATIC)
            assertEquals(PresetIds.CHROMATIC, settingsRepo.settings.first().activeTuningId)
            val after = awaitItem()
            assertTrue(assertNotNull(after.chromatic).isSelected)
            assertFalse(after.row(PresetIds.STANDARD).isSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noteLabelsHonourNotationAndCustomSubtitleIsTheDistanceFromStandard() = runTest {
        val (vm, _, settingsRepo) = viewModel(tunings = listOf(standard, mine))
        vm.uiState.test {
            val sharps = awaitLoaded().sections.first().rows.single()
            assertEquals(listOf("D2", "A2", "D3", "G3", "A♯3", "D♯4"), sharps.noteLabels)
            assertEquals("6th −2 · 2nd −1 · 1st −1", sharps.subtitle)

            settingsRepo.update { it.copy(notation = Notation.FLATS) }
            val flats = awaitItem().sections.first().rows.single()
            assertEquals(listOf("D2", "A2", "D3", "G3", "B♭3", "E♭4"), flats.noteLabels)
            assertEquals("6th −2 · 2nd −1 · 1st −1", flats.subtitle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presetRowsKeepTheirDescriptiveSubtitle() = runTest {
        val (vm, _, _) = viewModel(tunings = listOf(standard, openG))
        vm.uiState.test {
            val state = awaitLoaded()
            assertEquals("E A D G B E", state.row(PresetIds.STANDARD).subtitle)
            assertEquals("D G D G B D", state.row(openG.id).subtitle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun distanceFromStandardNamesStringsByGuitarNumberWithSignedSemitones() {
        assertEquals("", distanceFromStandard(listOf(40, 45, 50, 55, 59, 64)))
        assertEquals("6th −2", distanceFromStandard(listOf(38, 45, 50, 55, 59, 64)))
        assertEquals("6th −2 · 1st −2", distanceFromStandard(listOf(38, 45, 50, 55, 59, 62)))
        assertEquals("6th −2 · 3rd −1", distanceFromStandard(listOf(38, 45, 50, 54, 59, 64)))
        assertEquals("4th +2 · 2nd +1", distanceFromStandard(listOf(40, 45, 52, 55, 60, 64)))
        assertEquals(
            "6th −1 · 5th −1 · 4th −1 · 3rd −1 · 2nd −1 · 1st −1",
            distanceFromStandard(listOf(39, 44, 49, 54, 58, 63)),
        )
    }

    @Test
    fun buildSectionsIsPureAndSkipsChromatic() {
        val sections = buildSections(listOf(chromatic, standard, dropD, mine), activeId = mine.id, Notation.SHARPS)
        assertEquals(listOf(TuningGroup.MINE, TuningGroup.STANDARD, TuningGroup.POWER), sections.map { it.group })
        assertEquals(listOf(mine.id), sections[0].rows.map { it.id })
        assertTrue(sections[0].rows.single().isSelected)
        assertEquals(listOf(PresetIds.STANDARD), sections[1].rows.map { it.id })
        assertEquals(listOf(dropD.id), sections[2].rows.map { it.id })
    }

    @Test
    fun selectWritesActiveTuningIdAndMarksRow() = runTest {
        val (vm, _, settingsRepo) = viewModel()
        vm.uiState.test {
            val before = awaitLoaded()
            assertTrue(before.row(PresetIds.STANDARD).isSelected)
            assertFalse(before.row(dropD.id).isSelected)

            vm.select(dropD.id)

            assertEquals(dropD.id, settingsRepo.settings.first().activeTuningId)
            val after = awaitItem()
            assertTrue(after.row(dropD.id).isSelected)
            assertFalse(after.row(PresetIds.STANDARD).isSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun duplicateAddsCustomEntryAndReportsCopied() = runTest {
        val (vm, tuningsRepo, _) = viewModel()
        vm.messages.test {
            vm.duplicate(dropD.id)
            assertEquals(TuningsMessage.Copied, awaitItem())
        }
        val custom = tuningsRepo.tunings.first().filter { !it.isPreset }
        val copy = custom.single()
        assertTrue(copy.id.startsWith("custom_"))
        assertEquals(dropD.strings, copy.strings)
        assertEquals(TuningGroup.MINE, copy.group)
        vm.uiState.test {
            val mineRows = awaitLoaded().sections.first().rows
            assertEquals(listOf(copy.id), mineRows.map { it.id })
            assertTrue(mineRows.single().isCustom)
            assertEquals("6th −2", mineRows.single().subtitle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRemovesAndUndoRestores() = runTest {
        val (vm, tuningsRepo, _) = viewModel(tunings = listOf(standard, mine))
        vm.messages.test {
            vm.delete(mine.id)
            val message = assertIs<TuningsMessage.Deleted>(awaitItem())
            assertEquals(mine, message.tuning)
            assertFalse(message.wasActive)
            assertNull(tuningsRepo.tuning(mine.id).first())

            vm.undoDelete(message)
            assertEquals(mine, tuningsRepo.tuning(mine.id).first())
        }
    }

    @Test
    fun deletingTheActiveTuningFallsBackToStandardAndUndoReselects() = runTest {
        val (vm, _, settingsRepo) = viewModel(
            tunings = listOf(standard, mine),
            settings = TunerSettings(activeTuningId = mine.id),
        )
        vm.messages.test {
            vm.delete(mine.id)
            val message = assertIs<TuningsMessage.Deleted>(awaitItem())
            assertTrue(message.wasActive)
            assertEquals(PresetIds.STANDARD, settingsRepo.settings.first().activeTuningId)

            vm.undoDelete(message)
            assertEquals(mine.id, settingsRepo.settings.first().activeTuningId)
        }
    }

    @Test
    fun deleteIgnoresPresets() = runTest {
        val (vm, tuningsRepo, _) = viewModel()
        vm.messages.test {
            vm.delete(PresetIds.STANDARD)
            expectNoEvents()
        }
        assertNotNull(tuningsRepo.tuning(PresetIds.STANDARD).first())
    }

    private fun TuningsUiState.row(id: String): TuningRowUi = sections.flatMap { it.rows }.first { it.id == id }
}
