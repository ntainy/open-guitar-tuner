package dev.ntainy.guitar_tuner.ui.tuner

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.TunerState
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.fakes.FakeTunerEngine
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mic = AudioInputDevice(
        id = 0,
        key = AudioInputDevice.BUILTIN_KEY,
        name = "Built-in mic",
        kind = InputKind.BUILTIN_MIC,
    )
    private val usb = AudioInputDevice(
        id = 7,
        key = "usb:NUX MP-3",
        name = "NUX Mighty Plug Pro (USB)",
        kind = InputKind.USB,
    )

    private val engine = FakeTunerEngine(
        TunerState(permissionGranted = true, input = mic, availableInputs = listOf(mic, usb)),
    )
    private val settings = InMemorySettingsRepository()
    private val tunings = InMemoryTuningsRepository()
    private val testTone = MutableStateFlow(110.0)

    /** Same derivation as `AppContainer.activeTuning`, over the in-memory repositories. */
    private val activeTuning: Flow<Tuning> = settings.settings
        .map { it.activeTuningId }
        .distinctUntilChanged()
        .flatMapLatest { id -> tunings.tuning(id) }
        .map { it ?: InMemoryTuningsRepository.STANDARD }

    private fun viewModel() = TunerViewModel(engine, settings, activeTuning, testTone)

    /** Skips the placeholder the StateFlow starts with (it has no strings). */
    private suspend fun ReceiveTurbine<TunerUiState>.awaitDerived(): TunerUiState {
        var state = awaitItem()
        while (state.strings.isEmpty()) state = awaitItem()
        return state
    }

    @Test
    fun initialStateCombinesEngineSettingsAndTuning() = runTest {
        viewModel().uiState.test {
            val state = awaitDerived()
            assertEquals("Standard", state.tuningName)
            assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), state.strings.map { it.label })
            assertTrue(state.permissionGranted)
            assertTrue(state.autoMode)
            assertEquals(TunerUiState.HINT_PLAY, state.hint)
            assertEquals(mic, state.input)
            assertEquals(listOf(mic, usb), state.availableInputs)
            assertNull(state.selectedInputKey)
            assertEquals(110.0, state.testToneHz)
            assertEquals(HeadstockLayout.THREE_PLUS_THREE, state.headstockLayout)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun engineReadingsDriveHintAndTargetFlags() = runTest {
        viewModel().uiState.test {
            awaitDerived()

            engine.update { it.copy(targetIndex = 0, pitchHz = 81.5, centsOff = -18.0) }
            val flat = awaitItem()
            assertEquals(TunerUiState.HINT_FLAT, flat.hint)
            assertEquals("−18", flat.centsLabel)
            assertEquals("E2", flat.targetLabel)
            assertTrue(flat.strings[0].isTarget)

            engine.update { it.copy(targetIndex = 1, pitchHz = 110.8, centsOff = 12.5) }
            val sharp = awaitItem()
            assertEquals(TunerUiState.HINT_SHARP, sharp.hint)
            assertEquals("+13", sharp.centsLabel)
            assertTrue(sharp.strings[1].isTarget)
            assertFalse(sharp.strings[0].isTarget)

            engine.update { it.copy(centsOff = 0.5, inTune = true, tunedStrings = setOf(1)) }
            val inTune = awaitItem()
            assertEquals(TunerUiState.HINT_IN_TUNE, inTune.hint)
            assertTrue(inTune.inTune)
            assertTrue(inTune.strings[1].isTuned)
            assertTrue(inTune.anyTuned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectStringPinsTheTargetAndTurnsAutoOff() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitDerived()
            vm.selectString(2)
            val state = awaitItem()
            assertFalse(state.autoMode)
            assertEquals("D3", state.targetLabel)
            assertTrue(state.strings[2].isTarget)

            vm.setAutoMode(true)
            assertTrue(awaitItem().autoMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startOverClearsTunedMarks() = runTest {
        engine.update { it.copy(tunedStrings = setOf(0, 1, 2)) }
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitDerived().anyTuned)
            vm.startOver()
            val state = awaitItem()
            assertFalse(state.anyTuned)
            assertTrue(state.strings.none { it.isTuned })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectInputRecordsTheExplicitChoiceAndAutoClearsIt() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitDerived()
            vm.selectInput(usb)
            val explicit = expectMostRecentItem()
            assertEquals(usb.key, explicit.selectedInputKey)
            assertEquals(usb, explicit.input)
            assertEquals("USB", explicit.inputCaption)

            vm.selectInput(null)
            val auto = expectMostRecentItem()
            assertNull(auto.selectedInputKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testToneIsClampedAndReflected() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitDerived()
            vm.setTestToneHz(196.0)
            val g = awaitItem()
            assertEquals(196.0, g.testToneHz)
            assertEquals("G3", g.testToneNote)
            assertEquals(196.0, testTone.value)

            vm.setTestToneHz(9_999.0)
            assertEquals(TunerUiState.TEST_TONE_MAX_HZ, awaitItem().testToneHz)
            vm.setTestToneHz(1.0)
            assertEquals(TunerUiState.TEST_TONE_MIN_HZ, awaitItem().testToneHz)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsChangesPropagate() = runTest {
        viewModel().uiState.test {
            awaitDerived()
            settings.update { it.copy(notation = Notation.FLATS, activeTuningId = InMemoryTuningsRepository.DROP_D.id) }
            val state = expectMostRecentItem()
            assertEquals("Drop D", state.tuningName)
            assertEquals("D2", state.strings[0].label)

            settings.update {
                it.copy(headstockLayout = HeadstockLayout.SIX_IN_LINE, keepScreenOn = false, showHz = false)
            }
            val layout = expectMostRecentItem()
            assertEquals(HeadstockLayout.SIX_IN_LINE, layout.headstockLayout)
            assertFalse(layout.keepScreenOn)
            assertFalse(layout.showHz)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun flatNotationSpellsTheTargetNote() = runTest {
        val custom = Tuning(id = "custom_1", name = "Half step down", strings = listOf(39, 44, 49, 54, 58, 63))
        tunings.upsert(custom)
        settings.update { it.copy(notation = Notation.FLATS, activeTuningId = custom.id) }
        engine.update { it.copy(targetIndex = 0, pitchHz = 77.8, centsOff = 0.0, inTune = true) }
        viewModel().uiState.test {
            val state = awaitDerived()
            assertEquals("Half step down", state.tuningName)
            assertEquals("E♭2", state.targetLabel)
            assertEquals("E♭", state.targetLetter)
            assertEquals("2", state.targetOctave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun lifecycleAndPermissionCommandsReachTheEngine() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertFalse(awaitDerived().running)
            vm.start()
            assertTrue(awaitItem().running)
            vm.stop()
            assertFalse(awaitItem().running)
            vm.onPermissionResult(false)
            assertFalse(awaitItem().permissionGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun placeholderIsNeverShownOnceSubscribed() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitDerived()
            assertEquals(6, state.strings.size)
            assertEquals(TunerSettings().toleranceCents, state.toleranceCents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tappingThePinnedStringAgainReturnsToAuto() = runTest {
        val vm = viewModel()
        vm.onStringTap(2)
        assertFalse(engine.state.value.autoMode)
        assertEquals(2, engine.state.value.targetIndex)
        vm.onStringTap(4)
        assertFalse(engine.state.value.autoMode, "a different string re-pins instead of toggling")
        assertEquals(4, engine.state.value.targetIndex)
        vm.onStringTap(4)
        assertTrue(engine.state.value.autoMode, "tapping the pinned string hands control back to AUTO")
    }

    @Test
    fun allTunedEventFiresOnlyWhenTheLastMarkIsEarned() = runTest {
        val all = setOf(0, 1, 2, 3, 4, 5)
        engine.update { it.copy(tunedStrings = all) }
        val vm = viewModel()
        vm.events.test {
            expectNoEvents() // already all tuned when the screen (re)opens: no toast
            engine.startOver()
            expectNoEvents()
            engine.update { it.copy(tunedStrings = setOf(0, 1, 2, 3, 4)) }
            expectNoEvents()
            engine.update { it.copy(tunedStrings = all) }
            assertEquals(TunerEvent.AllTuned, awaitItem())
            engine.update { it.copy(pitchHz = 110.0) } // unrelated state change while still all tuned
            expectNoEvents()
        }
    }
}
