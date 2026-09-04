package dev.ntainy.guitar_tuner.ui.tunings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.audio.TunerEngine
import dev.ntainy.guitar_tuner.audio.TunerState
import dev.ntainy.guitar_tuner.data.SettingsRepository
import dev.ntainy.guitar_tuner.data.TuningsRepository
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.fakes.FakeTunerEngine
import dev.ntainy.guitar_tuner.fakes.InMemorySettingsRepository
import dev.ntainy.guitar_tuner.fakes.InMemoryTuningsRepository

/** Sample data for the Tunings, Editor and Settings previews. */
object PreviewData {
    val standard: Tuning = InMemoryTuningsRepository.STANDARD
    val dropD: Tuning = InMemoryTuningsRepository.DROP_D

    val dadgad = Tuning(
        id = "preset_dadgad",
        name = "D modal",
        subtitle = "D A D G A D",
        strings = listOf(38, 45, 50, 55, 57, 62),
        isPreset = true,
        group = TuningGroup.POWER,
    )
    val eFlat = Tuning(
        id = "preset_e_flat",
        name = "Half step down",
        subtitle = "E♭ A♭ D♭ G♭ B♭ E♭",
        strings = listOf(39, 44, 49, 54, 58, 63),
        isPreset = true,
        group = TuningGroup.TRANSPOSED,
    )
    val openG = Tuning(
        id = "preset_open_g",
        name = "Open G",
        subtitle = "D G D G B D",
        strings = listOf(38, 43, 50, 55, 59, 62),
        isPreset = true,
        group = TuningGroup.OPEN,
    )
    val nst = Tuning(
        id = "preset_nst",
        name = "NST",
        subtitle = "C G D A E G",
        strings = listOf(36, 43, 50, 57, 64, 67),
        isPreset = true,
        group = TuningGroup.EXTRAS,
    )
    val customOpenC = Tuning(
        id = "custom_open_c",
        name = "Open C (mine)",
        strings = listOf(36, 43, 48, 55, 60, 64),
        createdAt = 1L,
    )
    val customBaritone = Tuning(
        id = "custom_baritone",
        name = "Baritone B",
        strings = listOf(35, 40, 45, 50, 54, 59),
        createdAt = 2L,
    )

    val presets: List<Tuning> = listOf(standard, dropD, dadgad, eFlat, openG, nst)
    val withCustom: List<Tuning> = listOf(customOpenC, customBaritone) + presets

    val builtinMic = AudioInputDevice(id = 1, key = AudioInputDevice.BUILTIN_KEY, name = "Built-in microphone", kind = InputKind.BUILTIN_MIC)
    val usbInterface = AudioInputDevice(id = 7, key = "usb:NUX MP-3", name = "NUX Mighty Plug Pro (USB)", kind = InputKind.USB)
    val inputs: List<AudioInputDevice> = listOf(builtinMic, usbInterface)
}

/** [AppContainer] whose repositories and engine are seeded in-memory fakes. */
class PreviewAppContainer(
    context: Context,
    tunings: List<Tuning>,
    settings: TunerSettings,
    engineState: TunerState,
) : AppContainer(context) {
    override val settingsRepository: SettingsRepository = InMemorySettingsRepository(settings)
    override val tuningsRepository: TuningsRepository = InMemoryTuningsRepository(tunings)
    override val tunerEngine: TunerEngine = FakeTunerEngine(engineState)
}

/** A container for `@Preview`s that go through the real entry points and ViewModels. */
@Composable
fun previewContainer(
    tunings: List<Tuning> = PreviewData.withCustom,
    settings: TunerSettings = TunerSettings(activeTuningId = PreviewData.customOpenC.id),
    engineState: TunerState = TunerState(permissionGranted = true, availableInputs = PreviewData.inputs),
): AppContainer {
    val context = LocalContext.current
    return remember(tunings, settings, engineState) { PreviewAppContainer(context, tunings, settings, engineState) }
}
