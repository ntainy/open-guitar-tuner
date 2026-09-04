package dev.ntainy.guitar_tuner.ui.tuner

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.ui.theme.farOff
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.target
import java.util.Locale

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

/** Bottom sheet listing the inputs; "Auto" first, then every device, then the test-tone slider when that is active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputPickerSheet(
    state: TunerUiState,
    onSelectInput: (AudioInputDevice?) -> Unit,
    onTestToneHz: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        InputPickerContent(
            state = state,
            onSelectInput = onSelectInput,
            onTestToneHz = onTestToneHz,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
fun InputPickerContent(
    state: TunerUiState,
    onSelectInput: (AudioInputDevice?) -> Unit,
    onTestToneHz: (Double) -> Unit,
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
                    color = if (detail == "Active") scheme.inTune else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
