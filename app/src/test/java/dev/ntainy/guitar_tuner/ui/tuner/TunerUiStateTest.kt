package dev.ntainy.guitar_tuner.ui.tuner

import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.TunerState
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.presets.PresetTunings
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunerUiStateTest {
    private val standard = InMemoryTuningsRepository.STANDARD
    private val listening = TunerState(permissionGranted = true, running = true)

    private fun derive(
        engine: TunerState = listening,
        settings: TunerSettings = TunerSettings(),
        tuning: Tuning = standard,
        testToneHz: Double = 110.0,
        selectedInputKey: String? = null,
    ) = deriveTunerUiState(engine, settings, tuning, testToneHz, selectedInputKey)

    @Test
    fun stringLabelsFollowTheTuningAndNotation() {
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), derive().strings.map { it.label })

        val dropCSharp = standard.copy(id = "x", name = "Drop C♯", strings = listOf(37, 44, 49, 54, 58, 63))
        assertEquals("C♯2", derive(tuning = dropCSharp).strings[0].label)
        val flats = TunerSettings(notation = Notation.FLATS)
        assertEquals("D♭2", derive(tuning = dropCSharp, settings = flats).strings[0].label)
    }

    @Test
    fun stringFrequenciesUseTheReferencePitch() {
        assertEquals(110.0, derive().strings[1].frequencyHz, 1e-9)
        assertEquals(108.0, derive(settings = TunerSettings(a4Hz = 432.0)).strings[1].frequencyHz, 1e-9)
    }

    @Test
    fun targetAndTunedFlagsComeFromTheEngine() {
        val state = derive(engine = listening.copy(targetIndex = 2, tunedStrings = setOf(0, 2)))
        assertEquals(listOf(false, false, true, false, false, false), state.strings.map { it.isTarget })
        assertEquals(listOf(true, false, true, false, false, false), state.strings.map { it.isTuned })
        assertTrue(state.anyTuned)
        assertEquals("D3", state.targetLabel)
        assertEquals("D", state.targetLetter)
        assertEquals("3", state.targetOctave)
    }

    @Test
    fun noTargetMeansNoNote() {
        val state = derive()
        assertNull(state.targetLabel)
        assertNull(state.targetLetter)
        assertNull(state.targetOctave)
        assertFalse(state.anyTuned)
    }

    @Test
    fun hintSaysPlayAStringWithoutAPitch() {
        val state = derive(engine = listening.copy(targetIndex = 0))
        assertEquals(TunerUiState.HINT_PLAY, state.hint)
        assertNull(state.centsLabel)
        assertFalse(state.inTune)
    }

    @Test
    fun flatReadsTuneUpAndSharpReadsTuneDown() {
        val flat = derive(engine = listening.copy(targetIndex = 0, pitchHz = 81.5, centsOff = -18.2))
        assertEquals(TunerUiState.HINT_FLAT, flat.hint)
        assertEquals("−18", flat.centsLabel)
        assertFalse(flat.inTune)

        val sharp = derive(engine = listening.copy(targetIndex = 1, pitchHz = 110.7, centsOff = 11.6))
        assertEquals(TunerUiState.HINT_SHARP, sharp.hint)
        assertEquals("+12", sharp.centsLabel)
    }

    @Test
    fun insideToleranceIsInTuneEvenIfTheEngineFlagLags() {
        val engineSaysSo = derive(
            engine = listening.copy(targetIndex = 1, pitchHz = 110.1, centsOff = 1.4, inTune = true),
        )
        assertEquals(TunerUiState.HINT_IN_TUNE, engineSaysSo.hint)
        assertTrue(engineSaysSo.inTune)
        assertEquals("+1", engineSaysSo.centsLabel)

        val bandSaysSo = derive(
            engine = listening.copy(targetIndex = 1, pitchHz = 110.1, centsOff = 2.9, inTune = false),
        )
        assertTrue(bandSaysSo.inTune)
        assertEquals(TunerUiState.HINT_IN_TUNE, bandSaysSo.hint)

        val tighter = derive(
            engine = listening.copy(targetIndex = 1, pitchHz = 110.1, centsOff = 2.9, inTune = false),
            settings = TunerSettings(toleranceCents = 2.0),
        )
        assertFalse(tighter.inTune)
        assertEquals(TunerUiState.HINT_SHARP, tighter.hint)
    }

    @Test
    fun centsAreFormattedAsSignedIntegersWithARealMinus() {
        assertEquals("+3", formatCents(3.2))
        assertEquals("−12", formatCents(-12.4))
        assertEquals("0", formatCents(0.3))
        assertEquals("0", formatCents(-0.4))
        assertEquals("+50", formatCents(49.6))
    }

    @Test
    fun hzHasTwoDecimals() {
        assertEquals("110.19 Hz", formatHz(110.19))
        assertEquals("82.41 Hz", formatHz(82.407))
    }

    @Test
    fun settingsPassThrough() {
        val settings = TunerSettings(
            headstockLayout = HeadstockLayout.SIX_IN_LINE,
            keepScreenOn = false,
            showHz = false,
            toleranceCents = 5.0,
        )
        val state = derive(settings = settings)
        assertEquals(HeadstockLayout.SIX_IN_LINE, state.headstockLayout)
        assertFalse(state.keepScreenOn)
        assertFalse(state.showHz)
        assertEquals(5.0, state.toleranceCents)
        assertEquals("Standard", state.tuningName)
    }

    @Test
    fun inputsAndPermissionPassThrough() {
        val usb = AudioInputDevice(id = 3, key = "usb:NUX", name = "NUX Mighty Plug Pro (USB)", kind = InputKind.USB)
        val mic = AudioInputDevice(
            id = 0,
            key = AudioInputDevice.BUILTIN_KEY,
            name = "Built-in mic",
            kind = InputKind.BUILTIN_MIC,
        )
        val state = derive(
            engine = listening.copy(
                input = usb,
                availableInputs = listOf(mic, usb),
                error = "boom",
                permissionGranted = false,
            ),
            selectedInputKey = usb.key,
        )
        assertEquals(usb, state.input)
        assertEquals("USB", state.inputCaption)
        assertEquals(listOf(mic, usb), state.availableInputs)
        assertEquals(usb.key, state.selectedInputKey)
        assertEquals("boom", state.error)
        assertFalse(state.permissionGranted)
        assertFalse(state.isTestToneInput)
    }

    @Test
    fun inputCaptionsByKind() {
        fun device(kind: InputKind) = AudioInputDevice(id = 1, key = kind.name, name = kind.name, kind = kind)
        assertEquals(TunerUiState.CAPTION_NONE, inputCaption(null))
        assertEquals("Mic", inputCaption(device(InputKind.BUILTIN_MIC)))
        assertEquals("USB", inputCaption(device(InputKind.USB)))
        assertEquals("Headset", inputCaption(device(InputKind.WIRED_HEADSET)))
        assertEquals("Bluetooth", inputCaption(device(InputKind.BLUETOOTH)))
        assertEquals("Input", inputCaption(device(InputKind.OTHER)))
        assertEquals("Test tone", inputCaption(device(InputKind.TEST_TONE)))
        assertTrue(derive(engine = listening.copy(input = device(InputKind.TEST_TONE))).isTestToneInput)
    }

    @Test
    fun testToneNoteIsTheNearestNote() {
        assertEquals("A2", derive(testToneHz = 110.0).testToneNote)
        assertEquals("E2", derive(testToneHz = 82.41).testToneNote)
        assertEquals("A♯3", derive(testToneHz = 233.0).testToneNote)
        val flats = TunerSettings(notation = Notation.FLATS)
        assertEquals("B♭3", derive(testToneHz = 233.0, settings = flats).testToneNote)
        assertEquals(110.0, derive(testToneHz = 110.0).testToneHz)
    }

    @Test
    fun stringAccessibilityLabelsCountFromTheLowString() {
        val state = derive(engine = listening.copy(targetIndex = 0, tunedStrings = setOf(5)))
        assertEquals("String 6, E2, target", state.strings[0].accessibilityLabel)
        assertEquals("String 5, A2", state.strings[1].accessibilityLabel)
        assertEquals("String 1, E4, tuned", state.strings[5].accessibilityLabel)
        assertEquals(6, state.strings[0].number)
        assertEquals(1, state.strings[5].number)
    }

    @Test
    fun chromaticModeDropsTheStringsAndNamesTheNoteItHears() {
        val chromatic = checkNotNull(PresetTunings.byId(PresetIds.CHROMATIC))
        val state = derive(
            engine = listening.copy(pitchHz = 261.63, chromaticMidi = 60, centsOff = 0.4, inTune = true),
            tuning = chromatic,
        )

        assertTrue(state.chromatic)
        assertEquals(60, state.chromaticMidi)
        assertTrue(state.strings.isEmpty(), "there are no strings to draw in chromatic mode")
        assertEquals("C4", state.targetLabel)
        assertEquals("C", state.targetLetter)
        assertEquals("4", state.targetOctave)
        assertFalse(state.anyTuned)
        assertFalse(state.allTuned, "an empty tuning must not read as fully tuned")
    }

    @Test
    fun chromaticNoteNamesFollowTheNotationSetting() {
        val chromatic = checkNotNull(PresetTunings.byId(PresetIds.CHROMATIC))
        val sharp = listening.copy(pitchHz = 370.0, chromaticMidi = 66, centsOff = 0.0)
        assertEquals("F♯4", derive(engine = sharp, tuning = chromatic).targetLabel)
        assertEquals(
            "G♭4",
            derive(engine = sharp, settings = TunerSettings(notation = Notation.FLATS), tuning = chromatic).targetLabel,
        )
    }

    @Test
    fun aStringTuningIsNotChromatic() {
        val state = derive(engine = listening.copy(pitchHz = 110.0, targetIndex = 1, centsOff = 0.0))
        assertFalse(state.chromatic)
        assertNull(state.chromaticMidi)
        assertEquals(6, state.strings.size)
        assertEquals("A2", state.targetLabel)
    }

    @Test
    fun tuningNotesAreTheSixLettersWithoutOctavesInTheCurrentNotation() {
        assertEquals("E A D G B E", derive().tuningNotes)
        assertEquals("D A D G B E", derive(tuning = InMemoryTuningsRepository.DROP_D).tuningNotes)

        val halfStepDown = standard.copy(id = "x", name = "Half step down", strings = listOf(39, 44, 49, 54, 58, 63))
        assertEquals("D♯ G♯ C♯ F♯ A♯ D♯", derive(tuning = halfStepDown).tuningNotes)
        val flats = TunerSettings(notation = Notation.FLATS)
        assertEquals("E♭ A♭ D♭ G♭ B♭ E♭", derive(tuning = halfStepDown, settings = flats).tuningNotes)
    }

    @Test
    fun chromaticModeHasNoTuningNotesAndAsksForANoteRatherThanAString() {
        val chromatic = checkNotNull(PresetTunings.byId(PresetIds.CHROMATIC))
        val idle = derive(tuning = chromatic)
        assertEquals("", idle.tuningNotes)
        assertEquals(TunerUiState.HINT_PLAY_NOTE, idle.hint)
        assertNull(idle.readoutDetail)

        val heard = derive(
            engine = listening.copy(pitchHz = 261.63, chromaticMidi = 60, centsOff = 0.4),
            tuning = chromatic,
        )
        assertEquals(TunerUiState.HINT_IN_TUNE, heard.hint)
        assertEquals("C4 · 261.63 Hz", heard.readoutDetail)
    }

    @Test
    fun readoutDetailPairsTheTargetWithThePitchOnlyWhileHzAreShown() {
        assertNull(derive().readoutDetail, "nothing to say without a target")
        assertNull(readoutDetail(targetLabel = null, pitchHz = 110.0, showHz = true))

        val heard = derive(engine = listening.copy(targetIndex = 1, pitchHz = 111.523, centsOff = 24.0))
        assertEquals("A2 · 111.52 Hz", heard.readoutDetail)

        val hzOff = derive(
            engine = listening.copy(targetIndex = 1, pitchHz = 111.523, centsOff = 24.0),
            settings = TunerSettings(showHz = false),
        )
        assertEquals("A2", hzOff.readoutDetail)

        val pinnedButSilent = derive(engine = listening.copy(targetIndex = 2))
        assertEquals("D3", pinnedButSilent.readoutDetail, "a pinned string is named even before it is played")
        assertEquals(TunerUiState.HINT_PLAY, pinnedButSilent.hint)
    }
}
