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
import dev.ntainy.guitar_tuner.audio.ReferenceTonePlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

    /** Virtual clock for the in-tune tick's rate limit; tests move it by hand. */
    private var nowMs = 10_000L

    private fun viewModel(clock: () -> Long = { nowMs }) =
        TunerViewModel(engine, settings, activeTuning, testTone, now = clock)

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
            assertEquals(TunerEvent.StringTuned, awaitItem(), "marks earned before the last one are not the toast")
            engine.update { it.copy(tunedStrings = all) }
            assertEquals(TunerEvent.AllTuned, awaitItem())
            engine.update { it.copy(pitchHz = 110.0) } // unrelated state change while still all tuned
            expectNoEvents()
        }
    }

    @Test
    fun eachNewMarkFiresStringTunedAndClearingThemFiresNothing() = runTest {
        val vm = viewModel()
        vm.events.test {
            engine.update { it.copy(tunedStrings = setOf(0)) }
            assertEquals(TunerEvent.StringTuned, awaitItem())
            engine.update { it.copy(tunedStrings = setOf(0, 3)) }
            assertEquals(TunerEvent.StringTuned, awaitItem())
            engine.update { it.copy(tunedStrings = setOf(0, 3)) } // same set again
            expectNoEvents()
            engine.startOver() // losing marks is not something to celebrate
            expectNoEvents()
        }
    }

    @Test
    fun enteringTheInTuneBandTicksOnceAndIsRateLimited() = runTest {
        val vm = viewModel()
        vm.events.test {
            engine.update { it.copy(inTune = true) }
            assertEquals(TunerEvent.EnteredBand, awaitItem())
            // A needle sitting on the edge crosses repeatedly; within the window that must stay silent.
            repeat(4) {
                engine.update { it.copy(inTune = false) }
                engine.update { it.copy(inTune = true) }
            }
            expectNoEvents()
            nowMs += 5_000
            engine.update { it.copy(inTune = false) }
            engine.update { it.copy(inTune = true) }
            assertEquals(TunerEvent.EnteredBand, awaitItem(), "once the window has passed it ticks again")
        }
    }

    @Test
    fun stateThatIsAlreadyTrueWhenTheScreenOpensFiresNothing() = runTest {
        engine.update { it.copy(tunedStrings = setOf(0, 1), inTune = true) }
        val vm = viewModel()
        vm.events.test {
            expectNoEvents()
            engine.update { it.copy(pitchHz = 82.4) }
            expectNoEvents()
        }
    }

    @Test
    fun theTraceKeepsNoHistoryWhileItIsSwitchedOff() = runTest {
        settings.update { it.copy(showTrace = false) }
        val vm = viewModel { testScheduler.currentTime }
        backgroundScope.launch { vm.trace.collect {} }
        engine.update { it.copy(centsOff = 4.0) }
        advanceTimeBy(1_000)
        assertTrue(vm.trace.value.isEmpty(), "no history is kept while the trace is off")

        settings.update { it.copy(showTrace = true) }
        advanceTimeBy(1_000)
        assertTrue(vm.trace.value.size > 20, "about 25 samples a second once it is on")
    }

    @Test
    fun theTraceKeepsOnlyItsWindowAndBreaksWhereThePitchDropsOut() = runTest {
        settings.update { it.copy(showTrace = true) }
        val vm = viewModel { testScheduler.currentTime }
        backgroundScope.launch { vm.trace.collect {} }

        engine.update { it.copy(centsOff = 4.0) }
        advanceTimeBy(1_000)
        engine.update { it.copy(centsOff = null) } // the string dies away
        advanceTimeBy(1_000)

        val withGap = vm.trace.value
        assertTrue(withGap.any { it.cents != null }, "the sounding part is recorded")
        assertTrue(withGap.any { it.cents == null }, "silence is recorded as a gap, not as a value")

        // Well past the window: everything still held must be inside it.
        advanceTimeBy(TRACE_WINDOW_MS * 2)
        val points = vm.trace.value
        val newest = points.last().atMs
        assertTrue(points.all { newest - it.atMs <= TRACE_WINDOW_MS }, "older readings are dropped")
        assertTrue(points.size < TRACE_WINDOW_MS / 40 + 5, "the buffer does not grow without bound")
    }

    /** Plays for exactly as long as the test says, so the capture hand-off can be checked at each edge. */
    private class FakeReferenceTone : ReferenceTonePlayer {
        private val _playing = MutableStateFlow(false)
        override val playing: StateFlow<Boolean> = _playing.asStateFlow()
        val played = mutableListOf<Double>()
        private var note = CompletableDeferred<Unit>()

        override suspend fun play(frequencyHz: Double) {
            played += frequencyHz
            _playing.value = true
            try {
                note.await()
            } finally {
                _playing.value = false
            }
        }

        override fun stop() = endNote()

        fun endNote() {
            note.complete(Unit)
            note = CompletableDeferred()
        }
    }

    @Test
    fun aReferenceToneSilencesTheMicAndGivesItBackWhenTheNoteEnds() = runTest {
        val tone = FakeReferenceTone()
        val vm = TunerViewModel(engine, settings, activeTuning, testTone, tone)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.start()
        assertTrue(engine.state.value.running)

        val lowE = vm.uiState.value.strings.first().frequencyHz
        vm.playReferenceTone(0)
        runCurrent()
        assertEquals(listOf(lowE), tone.played, "it plays the target pitch of the string that was held")
        assertFalse(engine.state.value.running, "capture stops, or the tuner would tune to the phone's own speaker")

        tone.endNote()
        runCurrent()
        assertTrue(engine.state.value.running, "the mic comes back once the note has finished")
    }

    @Test
    fun aNoteFinishingAfterTheScreenIsGoneDoesNotReopenTheMic() = runTest {
        val tone = FakeReferenceTone()
        val vm = TunerViewModel(engine, settings, activeTuning, testTone, tone)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.start()

        vm.playReferenceTone(2)
        runCurrent()
        vm.stop() // the user leaves the Tune screen while the note is still ringing
        tone.endNote()
        runCurrent()
        assertFalse(engine.state.value.running, "a finished note must not quietly restart capture")
    }

    @Test
    fun overlappingReferenceTonesResumeCaptureOnlyOnce() = runTest {
        val tone = FakeReferenceTone()
        val vm = TunerViewModel(engine, settings, activeTuning, testTone, tone)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.start()

        vm.playReferenceTone(0)
        vm.playReferenceTone(1)
        runCurrent()
        assertEquals(2, tone.played.size)
        assertFalse(engine.state.value.running)

        tone.endNote() // both notes are waiting on the same gate
        runCurrent()
        assertTrue(engine.state.value.running)
    }
}
