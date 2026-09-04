package dev.ntainy.guitar_tuner.ui.settings

import app.cash.turbine.test
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.TunerState
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.InputPolicy
import dev.ntainy.guitar_tuner.data.model.ThemeMode
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.fakes.FakeTunerEngine
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.ui.tunings.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mic = AudioInputDevice(1, AudioInputDevice.BUILTIN_KEY, "Built-in microphone", InputKind.BUILTIN_MIC)
    private val usb = AudioInputDevice(7, "usb:NUX MP-3", "NUX Mighty Plug Pro (USB)", InputKind.USB)

    private class Fixture(initial: TunerSettings = TunerSettings()) {
        val settings = InMemorySettingsRepository(initial)
        val engine = FakeTunerEngine(TunerState(permissionGranted = true))
        val vm = SettingsViewModel(settings, engine, appVersion = "9.9-test")

        suspend fun current(): TunerSettings = settings.settings.first()
    }

    @Test
    fun uiStateMirrorsSettingsInputsAndVersion() = runTest {
        val f = Fixture(TunerSettings(a4Hz = 442.0))
        f.vm.uiState.test {
            var state = awaitItem()
            while (state.settings.a4Hz != 442.0) state = awaitItem()
            assertEquals("9.9-test", state.appVersion)
            assertTrue(state.availableInputs.isEmpty())

            f.engine.update { it.copy(availableInputs = listOf(mic, usb)) }
            assertEquals(listOf(mic, usb), awaitItem().availableInputs)

            f.engine.update { it.copy(pitchHz = 110.0) }
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun referencePitchStepsByOneHzAndStopsAtTheBounds() = runTest {
        val f = Fixture()
        f.vm.stepA4Hz(+1)
        assertEquals(441.0, f.current().a4Hz)
        f.vm.stepA4Hz(-2)
        assertEquals(439.0, f.current().a4Hz)

        f.vm.setA4Hz(TunerSettings.MAX_A4_HZ)
        f.vm.stepA4Hz(+1)
        assertEquals(TunerSettings.MAX_A4_HZ, f.current().a4Hz)

        f.vm.setA4Hz(TunerSettings.MIN_A4_HZ)
        f.vm.stepA4Hz(-1)
        assertEquals(TunerSettings.MIN_A4_HZ, f.current().a4Hz)
    }

    @Test
    fun referencePitchIsSnappedToWholeHzAndClamped() = runTest {
        val f = Fixture()
        f.vm.setA4Hz(443.4)
        assertEquals(443.0, f.current().a4Hz)
        f.vm.setA4Hz(500.0)
        assertEquals(TunerSettings.MAX_A4_HZ, f.current().a4Hz)
        f.vm.setA4Hz(100.0)
        assertEquals(TunerSettings.MIN_A4_HZ, f.current().a4Hz)
        f.vm.resetA4Hz()
        assertEquals(440.0, f.current().a4Hz)
    }

    @Test
    fun toleranceIsWholeCentsWithinBounds() = runTest {
        val f = Fixture()
        f.vm.setToleranceCents(5.4)
        assertEquals(5.0, f.current().toleranceCents)
        f.vm.setToleranceCents(0.0)
        assertEquals(TunerSettings.MIN_TOLERANCE_CENTS, f.current().toleranceCents)
        f.vm.setToleranceCents(40.0)
        assertEquals(TunerSettings.MAX_TOLERANCE_CENTS, f.current().toleranceCents)
    }

    @Test
    fun everyDisplayAndNotationUpdateWritesThrough() = runTest {
        val f = Fixture()
        f.vm.setNotation(Notation.FLATS)
        assertEquals(Notation.FLATS, f.current().notation)
        f.vm.setHeadstockLayout(HeadstockLayout.SIX_IN_LINE)
        assertEquals(HeadstockLayout.SIX_IN_LINE, f.current().headstockLayout)
        f.vm.setTheme(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, f.current().theme)
        f.vm.setShowHz(false)
        assertFalse(f.current().showHz)
        f.vm.setKeepScreenOn(false)
        assertFalse(f.current().keepScreenOn)
    }

    @Test
    fun inputPolicyAndPreferredDeviceWriteThrough() = runTest {
        val f = Fixture()
        f.vm.setInputPolicy(InputPolicy.BUILTIN_MIC)
        assertEquals(InputPolicy.BUILTIN_MIC, f.current().inputPolicy)

        f.vm.setPreferredInput(usb.key)
        val specific = f.current()
        assertEquals(InputPolicy.SPECIFIC_DEVICE, specific.inputPolicy)
        assertEquals(usb.key, specific.preferredInputKey)

        f.vm.setInputPolicy(InputPolicy.PREFER_USB)
        val back = f.current()
        assertEquals(InputPolicy.PREFER_USB, back.inputPolicy)
        assertEquals(usb.key, back.preferredInputKey, "the remembered device survives a policy change")
    }

    @Test
    fun updatesOnlyTouchTheirOwnField() = runTest {
        val f = Fixture(TunerSettings(activeTuningId = "custom_x", a4Hz = 432.0))
        f.vm.setTheme(ThemeMode.SYSTEM)
        val after = f.current()
        assertEquals("custom_x", after.activeTuningId)
        assertEquals(432.0, after.a4Hz)
        assertEquals(ThemeMode.SYSTEM, after.theme)
    }
}
