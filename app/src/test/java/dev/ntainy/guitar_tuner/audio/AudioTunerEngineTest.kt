package dev.ntainy.guitar_tuner.audio

import dev.ntainy.guitar_tuner.data.model.InputPolicy
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AudioTunerEngineTest {

    /**
     * Engine plus every fake, wired on the test scheduler. The engine lives in `backgroundScope` (cancelled
     * when the test ends), whose tasks `advanceUntilIdle()` skips, so [settle] drives the chain instead.
     */
    private class Harness(
        private val scope: TestScope,
        devices: List<AudioInputDevice>,
        settings: TunerSettings = TunerSettings(),
        factory: RecordingSourceFactory = RecordingSourceFactory(),
        holdOffFrames: Int = 0,
    ) {
        val monitor = FakeAudioInputMonitor(devices)
        val factory = factory
        val detector = ScriptedPitchDetector(fallback = A2_HZ)
        val smoother = IdentitySmoother()
        val resolver = NearestStringResolver()
        val assembler = PassThroughAssembler()
        val settings = MutableStateFlow(settings)
        val tuning = MutableStateFlow(InMemoryTuningsRepository.STANDARD)
        val engine = AudioTunerEngine(
            scope = scope.backgroundScope,
            inputMonitor = monitor,
            sourceFactory = this.factory,
            detector = detector,
            smoother = smoother,
            resolver = resolver,
            assembler = assembler,
            tuningFlow = tuning,
            settingsFlow = this.settings,
            analysisDispatcher = StandardTestDispatcher(scope.testScheduler),
            holdOffFrames = holdOffFrames,
        )

        val state: TunerState get() = engine.state.value

        /** Runs every pending task, foreground and background, including the engine's retry delay. */
        fun settle() {
            scope.testScheduler.advanceTimeBy(1_000)
            scope.testScheduler.runCurrent()
        }

        fun startWithPermission() {
            engine.onPermissionResult(true)
            engine.start()
        }

        /** Pushes [times] chunks into the source currently being captured. */
        fun feed(times: Int = 1, amplitude: Float = 0.1f) = factory.latest.send(chunkOf(amplitude), times)
    }

    @Test
    fun noCaptureWithoutPermission() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.engine.start()
        h.settle()

        assertTrue(h.factory.devices.isEmpty(), "no source should be created without permission")
        assertFalse(h.state.running)
        assertFalse(h.state.permissionGranted)
        assertEquals(MIC_DEVICE, h.state.input, "the input is chosen even while idle")
        assertEquals(listOf(MIC_DEVICE), h.state.availableInputs)

        h.engine.onPermissionResult(true)
        h.settle()

        assertEquals(listOf(MIC_DEVICE), h.factory.devices)
        assertTrue(h.factory.latest.collected)
        assertTrue(h.state.running)
        assertTrue(h.state.permissionGranted)
    }

    @Test
    fun captureProducesPitchCentsAndTarget() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()
        h.feed()
        h.settle()

        val s = h.state
        assertEquals(A2_HZ, s.pitchHz)
        assertEquals(1, s.targetIndex, "110 Hz is the A string of Standard")
        assertEquals(0.0, assertNotNull(s.centsOff), 1e-9)
        assertTrue(s.inTune)
        assertEquals(ScriptedPitchDetector.CONFIDENCE, s.confidence)
        assertEquals(0.1, s.level, 1e-3)
        assertTrue(s.running)
        assertNull(s.error)

        h.detector.queue.add(112.0)
        h.feed()
        h.settle()
        assertEquals(1, h.state.targetIndex)
        assertEquals(1200.0 * kotlin.math.ln(112.0 / 110.0) / kotlin.math.ln(2.0), assertNotNull(h.state.centsOff), 1e-9)
        assertFalse(h.state.inTune)

        h.detector.queue.add(null)
        h.feed()
        h.settle()
        assertNull(h.state.pitchHz)
        assertNull(h.state.centsOff)
        assertFalse(h.state.inTune)
        assertEquals(1, h.state.targetIndex, "silence keeps the last target")
        assertEquals(0.0, h.state.confidence)
    }

    @Test
    fun preferUsbPicksUsbAndFallsBackToMicWhenRemoved() = runTest {
        val h = Harness(this, listOf(USB_DEVICE, MIC_DEVICE))
        h.startWithPermission()
        h.settle()

        assertEquals(USB_DEVICE, h.state.input)
        assertEquals(listOf(USB_DEVICE), h.factory.devices)

        h.monitor.list.value = listOf(MIC_DEVICE)
        h.settle()

        assertEquals(MIC_DEVICE, h.state.input)
        assertEquals(listOf(USB_DEVICE, MIC_DEVICE), h.factory.devices)
        assertTrue(h.factory.sources[0].cancelled, "the USB source must be cancelled on unplug")
        assertTrue(h.factory.sources[1].collected)
        assertTrue(h.state.running)
        assertEquals(listOf(MIC_DEVICE), h.state.availableInputs)

        h.monitor.list.value = listOf(USB_DEVICE, MIC_DEVICE)
        h.settle()

        assertEquals(USB_DEVICE, h.state.input)
        assertEquals(listOf(USB_DEVICE, MIC_DEVICE, USB_DEVICE), h.factory.devices)
        assertTrue(h.factory.sources[1].cancelled)
    }

    @Test
    fun builtinMicPolicyIgnoresUsb() = runTest {
        val h = Harness(this, listOf(USB_DEVICE, MIC_DEVICE), TunerSettings(inputPolicy = InputPolicy.BUILTIN_MIC))
        h.startWithPermission()
        h.settle()

        assertEquals(MIC_DEVICE, h.state.input)
        assertEquals(listOf(MIC_DEVICE), h.factory.devices)
    }

    @Test
    fun specificDevicePolicyPicksByKeyAndFallsBackToPreferUsb() = runTest {
        val settings = TunerSettings(inputPolicy = InputPolicy.SPECIFIC_DEVICE, preferredInputKey = WIRED_DEVICE.key)
        val h = Harness(this, listOf(USB_DEVICE, MIC_DEVICE, WIRED_DEVICE), settings)
        h.startWithPermission()
        h.settle()

        assertEquals(WIRED_DEVICE, h.state.input)
        assertEquals(listOf(WIRED_DEVICE), h.factory.devices)

        h.monitor.list.value = listOf(USB_DEVICE, MIC_DEVICE)
        h.settle()

        assertEquals(USB_DEVICE, h.state.input, "a missing specific device behaves like PREFER_USB")
        assertEquals(listOf(WIRED_DEVICE, USB_DEVICE), h.factory.devices)
        assertTrue(h.factory.sources[0].cancelled)
    }

    @Test
    fun selectInputOverridesPolicyAndFallsBackWhenItDisappears() = runTest {
        val h = Harness(this, listOf(USB_DEVICE, MIC_DEVICE))
        h.startWithPermission()
        h.settle()
        assertEquals(USB_DEVICE, h.state.input)

        h.engine.selectInput(MIC_DEVICE)
        h.settle()

        assertEquals(MIC_DEVICE, h.state.input)
        assertEquals(listOf(USB_DEVICE, MIC_DEVICE), h.factory.devices)
        assertTrue(h.factory.sources[0].cancelled)

        h.monitor.list.value = listOf(USB_DEVICE)
        h.settle()

        assertEquals(USB_DEVICE, h.state.input, "policy takes over when the selected device is gone")
        assertEquals(listOf(USB_DEVICE, MIC_DEVICE, USB_DEVICE), h.factory.devices)
        assertTrue(h.factory.sources[1].cancelled)

        h.engine.selectInput(null)
        h.settle()
        assertEquals(USB_DEVICE, h.state.input)
        assertEquals(3, h.factory.devices.size, "clearing the override must not restart an unchanged input")
    }

    @Test
    fun manualStringPinsTargetUntilAutoIsReenabled() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()
        h.feed()
        h.settle()
        assertEquals(1, h.state.targetIndex)
        assertTrue(h.state.autoMode)

        h.engine.selectString(0)
        assertEquals(0, h.state.targetIndex)
        assertFalse(h.state.autoMode)

        h.feed()
        h.settle()
        assertEquals(0, h.state.targetIndex, "manual target must not follow the pitch")
        assertEquals(500.0, assertNotNull(h.state.centsOff), 1e-6)
        assertFalse(h.state.inTune)

        val resetsBefore = h.resolver.resets
        h.engine.setAutoMode(true)
        assertTrue(h.state.autoMode)
        assertTrue(h.resolver.resets > resetsBefore, "re-enabling AUTO resets the resolver")

        h.feed()
        h.settle()
        assertEquals(1, h.state.targetIndex)
        assertEquals(0.0, assertNotNull(h.state.centsOff), 1e-9)
    }

    @Test
    fun stringIsMarkedTunedAfterEightInTuneFramesAndClearedByStartOver() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()

        h.feed(times = 7)
        h.settle()
        assertEquals(emptySet(), h.state.tunedStrings, "seven frames are not enough")

        h.feed()
        h.settle()
        assertEquals(setOf(1), h.state.tunedStrings)

        h.engine.startOver()
        assertEquals(emptySet(), h.state.tunedStrings)

        h.feed(times = 7)
        h.settle()
        assertEquals(emptySet(), h.state.tunedStrings, "start over also restarts the counter")

        h.feed()
        h.settle()
        assertEquals(setOf(1), h.state.tunedStrings)
    }

    @Test
    fun tunedCounterRestartsAfterAnOutOfTuneFrame() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()

        h.feed(times = 5)
        h.settle()
        h.detector.queue.add(115.0)
        h.feed()
        h.settle()
        assertFalse(h.state.inTune)
        h.feed(times = 7)
        h.settle()
        assertEquals(emptySet(), h.state.tunedStrings, "the out-of-tune frame restarts the count")

        h.feed()
        h.settle()
        assertEquals(setOf(1), h.state.tunedStrings)
    }

    @Test
    fun tuningChangeClearsTunedMarksAndResetsResolver() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()
        h.feed(times = 8)
        h.settle()
        assertEquals(setOf(1), h.state.tunedStrings)

        val resetsBefore = h.resolver.resets
        h.tuning.value = InMemoryTuningsRepository.DROP_D
        h.settle()

        assertEquals(emptySet(), h.state.tunedStrings)
        assertNull(h.state.targetIndex, "AUTO mode clears the target on a tuning change")
        assertTrue(h.resolver.resets > resetsBefore)

        h.feed()
        h.settle()
        assertEquals(1, h.state.targetIndex, "A2 is still string 1 in Drop D")
    }

    @Test
    fun stopClearsReadingsResetsDspAndCancelsSource() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()
        h.feed()
        h.settle()
        assertEquals(A2_HZ, h.state.pitchHz)

        h.engine.stop()
        assertFalse(h.state.running)
        assertNull(h.state.pitchHz)
        assertNull(h.state.centsOff)
        assertFalse(h.state.inTune)
        assertEquals(0.0, h.state.level)
        assertTrue(h.smoother.resets > 0)
        assertTrue(h.assembler.resets > 0)

        h.settle()
        assertTrue(h.factory.sources[0].cancelled)
        assertEquals(1, h.factory.devices.size)

        h.engine.start()
        h.settle()
        assertTrue(h.state.running)
        assertEquals(2, h.factory.devices.size, "start() after stop() opens a fresh source")
    }

    @Test
    fun permissionRevokedStopsCapture() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE))
        h.startWithPermission()
        h.settle()
        assertTrue(h.state.running)

        h.engine.onPermissionResult(false)
        h.settle()

        assertFalse(h.state.running)
        assertFalse(h.state.permissionGranted)
        assertTrue(h.factory.sources[0].cancelled)
    }

    @Test
    fun sourceErrorLandsInStateAndStartRetries() = runTest {
        val failing = RecordingSourceFactory { FakeAudioSource(it, failure = AudioSourceException("boom")) }
        val h = Harness(this, listOf(MIC_DEVICE), factory = failing)
        h.startWithPermission()
        h.settle()

        assertEquals("boom", h.state.error)
        assertFalse(h.state.running)
        assertNull(h.state.pitchHz)
        assertEquals(2, h.factory.devices.size, "one automatic retry before giving up")

        h.engine.start()
        assertNull(h.state.error, "start() clears the previous error")
        h.settle()

        assertEquals("boom", h.state.error)
        assertEquals(4, h.factory.devices.size, "start() after an error tries again")
    }

    @Test
    fun transientSourceErrorIsRetriedAutomatically() = runTest {
        var calls = 0
        val flaky = RecordingSourceFactory { device ->
            calls++
            if (calls == 1) FakeAudioSource(device, failure = AudioSourceException("hiccup")) else FakeAudioSource(device)
        }
        val h = Harness(this, listOf(MIC_DEVICE), factory = flaky)
        h.startWithPermission()
        h.settle()

        assertNull(h.state.error)
        assertTrue(h.state.running)
        assertEquals(2, h.factory.devices.size)

        h.feed()
        h.settle()
        assertEquals(A2_HZ, h.state.pitchHz)
    }

    @Test
    fun chooseInputCoversEveryPolicy() {
        val all = listOf(USB_DEVICE, MIC_DEVICE, WIRED_DEVICE)
        val preferUsb = TunerSettings(inputPolicy = InputPolicy.PREFER_USB)
        val builtin = TunerSettings(inputPolicy = InputPolicy.BUILTIN_MIC)
        val specific = TunerSettings(inputPolicy = InputPolicy.SPECIFIC_DEVICE, preferredInputKey = WIRED_DEVICE.key)

        assertNull(AudioTunerEngine.chooseInput(emptyList(), null, preferUsb))
        assertEquals(USB_DEVICE, AudioTunerEngine.chooseInput(all, null, preferUsb))
        assertEquals(MIC_DEVICE, AudioTunerEngine.chooseInput(listOf(MIC_DEVICE, WIRED_DEVICE), null, preferUsb))
        assertEquals(MIC_DEVICE, AudioTunerEngine.chooseInput(all, null, builtin))
        assertEquals(USB_DEVICE, AudioTunerEngine.chooseInput(listOf(USB_DEVICE, WIRED_DEVICE), null, builtin))
        assertEquals(WIRED_DEVICE, AudioTunerEngine.chooseInput(all, null, specific))
        assertEquals(USB_DEVICE, AudioTunerEngine.chooseInput(listOf(USB_DEVICE, MIC_DEVICE), null, specific))
        assertEquals(WIRED_DEVICE, AudioTunerEngine.chooseInput(all, WIRED_DEVICE, builtin))
        assertEquals(MIC_DEVICE, AudioTunerEngine.chooseInput(listOf(USB_DEVICE, MIC_DEVICE), WIRED_DEVICE, builtin))
        assertEquals(WIRED_DEVICE, AudioTunerEngine.chooseInput(listOf(WIRED_DEVICE), null, builtin), "last resort")
    }

    private companion object {
        const val A2_HZ = 110.0
    }

    @Test
    fun attackTransientIsHeldBackForHoldOffFrames() = runTest {
        val h = Harness(this, listOf(MIC_DEVICE), holdOffFrames = 3)
        h.startWithPermission()
        h.settle()
        repeat(3) {
            h.feed()
            h.settle()
            assertNull(h.state.pitchHz, "frame ${it + 1} of the attack must not be shown")
        }
        h.feed()
        h.settle()
        assertEquals(A2_HZ, h.state.pitchHz, "first frame after the hold-off is shown")
    }
}
