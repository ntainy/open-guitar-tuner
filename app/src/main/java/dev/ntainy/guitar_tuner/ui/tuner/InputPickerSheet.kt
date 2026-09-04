package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.ui.theme.farOff
import dev.ntainy.guitar_tuner.ui.theme.target
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

/** Icon for an input kind, shared by the header button and the sheet rows. */
val InputKind.icon: ImageVector
    get() = when (this) {
        InputKind.BUILTIN_MIC -> Icons.Outlined.Mic
        InputKind.USB -> Icons.Outlined.Usb
        InputKind.WIRED_HEADSET -> Icons.Outlined.Headset
        InputKind.BLUETOOTH -> Icons.Outlined.Bluetooth
        InputKind.OTHER -> Icons.Outlined.SettingsInputComponent
        InputKind.TEST_TONE -> Icons.Outlined.GraphicEq
    }

/**
 * Bottom sheet listing the inputs: "Auto" first, then every device, then the active input's sensitivity with a live
 * level meter (or the test-tone slider when that is what is playing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputPickerSheet(
    state: TunerUiState,
    onSelectInput: (AudioInputDevice?) -> Unit,
    onTestToneHz: (Double) -> Unit,
    onSensitivityDb: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        InputPickerContent(
            state = state,
            onSelectInput = onSelectInput,
            onTestToneHz = onTestToneHz,
            onSensitivityDb = onSensitivityDb,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
fun InputPickerContent(
    state: TunerUiState,
    onSelectInput: (AudioInputDevice?) -> Unit,
    onTestToneHz: (Double) -> Unit,
    onSensitivityDb: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val active = state.input
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = "Input",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = active?.let { "Listening on ${it.name}" } ?: "No input available",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (state.error != null) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.farOff,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        InputRow(
            icon = Icons.Outlined.AutoAwesome,
            name = "Auto",
            detail = "USB when connected, otherwise the microphone",
            selected = state.selectedInputKey == null,
            onClick = { onSelectInput(null) },
        )
        state.availableInputs.forEach { device ->
            InputRow(
                icon = device.kind.icon,
                name = device.name,
                detail = if (device == active) "Active" else null,
                selected = state.selectedInputKey == device.key,
                onClick = { onSelectInput(device) },
            )
        }
        if (state.canAdjustSensitivity && active != null) {
            SensitivityControls(
                sensitivityDb = state.sensitivityDb,
                level = state.inputLevel,
                gateLevel = state.gateLevel,
                inputName = active.name,
                onSensitivityDb = onSensitivityDb,
            )
        }
        if (state.isTestToneInput) {
            TestToneControls(hz = state.testToneHz, note = state.testToneNote, onTestToneHz = onTestToneHz)
        }
    }
}

@Composable
private fun InputRow(
    icon: ImageVector,
    name: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) scheme.target else scheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detail == "Active") scheme.target else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Sensitivity for the input being listened to, with a level meter so the value can be judged rather than guessed:
 * the bar is what the tuner hears after the boost, the mark is the level a note must clear to be trusted. A dry
 * pickup through a USB interface can sit 30 dB below a phone microphone, so the value belongs to the input and is
 * remembered for it alone.
 *
 * Whole decibels; the value is applied as the thumb moves (the needle and the bar answer at once), and the thumb
 * lands on whole numbers without tick marks, like the reference-pitch slider in Settings.
 */
@Composable
private fun SensitivityControls(
    sensitivityDb: Double,
    level: Double,
    gateLevel: Double,
    inputName: String,
    onSensitivityDb: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalTunerHaptics.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    var lastStep by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val shown = dragging?.roundToInt() ?: sensitivityDb.roundToInt()
    val label = formatDb(shown)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Sensitivity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.target,
            )
        }
        Slider(
            value = dragging ?: sensitivityDb.toFloat(),
            onValueChange = { raw ->
                val whole = raw.roundToInt()
                dragging = whole.toFloat()
                if (whole != lastStep) {
                    lastStep = whole
                    haptics.step()
                    onSensitivityDb(whole.toDouble())
                }
            },
            onValueChangeFinished = {
                dragging = null
                haptics.gestureEnd()
            },
            valueRange = TunerSettings.MIN_SENSITIVITY_DB.toFloat()..TunerSettings.MAX_SENSITIVITY_DB.toFloat(),
            steps = 0,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Sensitivity, $label" },
        )
        InputLevelMeter(
            level = level,
            gateLevel = gateLevel,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Text(
            text = "Play a string softly and raise this until the bar clears the mark. Remembered for $inputName.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * The input's level after sensitivity as a bar over the last [METER_RANGE_DB] decibels, with the gate's threshold
 * drawn as a mark: a bar past the mark is a note the tuner will read, a bar short of it is silence to the tuner.
 * Brass while the input is heard, dim while it is not.
 */
@Composable
private fun InputLevelMeter(level: Double, gateLevel: Double, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val heard = gateLevel > 0.0 && level >= gateLevel
    val fill by animateFloatAsState(
        targetValue = meterFraction(level),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "inputLevel",
    )
    val mark = meterFraction(gateLevel)
    val barColor = if (heard) scheme.target else scheme.onSurfaceVariant
    val trackColor = scheme.surfaceContainerHighest
    val markColor = scheme.onSurface
    val description = if (heard) "Input heard" else "Input below the gate"
    Canvas(
        modifier = modifier
            .height(8.dp)
            .semantics {
                contentDescription = "Input level"
                stateDescription = description
            },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (fill > 0f) {
            drawRoundRect(color = barColor, size = Size(size.width * fill, size.height), cornerRadius = radius)
        }
        if (mark > 0f) {
            val x = size.width * mark
            val overhang = 2.dp.toPx()
            drawLine(
                color = markColor,
                start = Offset(x, -overhang),
                end = Offset(x, size.height + overhang),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

/** Where a linear RMS level sits on the meter: 0 at −[METER_RANGE_DB] dBFS, 1 at full scale. */
internal fun meterFraction(level: Double): Float =
    if (level <= 0.0) 0f else ((20.0 * log10(level) + METER_RANGE_DB) / METER_RANGE_DB).coerceIn(0.0, 1.0).toFloat()

/** "+12 dB", "0 dB", "−6 dB": a real minus sign, like the cents readout. */
internal fun formatDb(db: Int): String = when {
    db > 0 -> "+$db dB"
    db < 0 -> "−${-db} dB"
    else -> "0 dB"
}

/** The meter spans the 60 dB below full scale: room noise sits near the left end, a plucked string past the middle. */
private const val METER_RANGE_DB = 60.0

@Composable
private fun TestToneControls(hz: Double, note: String, onTestToneHz: (Double) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Test tone", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                text = String.format(Locale.US, "%.1f Hz", hz),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(text = note, style = MaterialTheme.typography.titleMedium, color = scheme.target)
        }
        Slider(
            value = hz.toFloat(),
            onValueChange = { onTestToneHz(it.toDouble()) },
            valueRange = TunerUiState.TEST_TONE_MIN_HZ.toFloat()..TunerUiState.TEST_TONE_MAX_HZ.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Test tone frequency" },
        )
    }
}
