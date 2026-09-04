@file:OptIn(ExperimentalMaterial3Api::class)

package dev.ntainy.guitar_tuner.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.audio.InputKind
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.InputPolicy
import dev.ntainy.guitar_tuner.data.model.ThemeMode
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme
import dev.ntainy.guitar_tuner.ui.tunings.PreviewData
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.ntainy.guitar_tuner.R
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.ui.tunings.SectionHeader
import dev.ntainy.guitar_tuner.ui.tunings.previewContainer
import kotlin.math.roundToInt

/** Every user setting, grouped into Tuning / Display / Input / About. */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, container.tunerEngine) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        actions = SettingsActions(
            onA4Change = viewModel::setA4Hz,
            onA4Step = viewModel::stepA4Hz,
            onA4Reset = viewModel::resetA4Hz,
            onToleranceChange = viewModel::setToleranceCents,
            onNotationChange = viewModel::setNotation,
            onHeadstockChange = viewModel::setHeadstockLayout,
            onThemeChange = viewModel::setTheme,
            onShowHzChange = viewModel::setShowHz,
            onHapticsChange = viewModel::setHaptics,
            onShowTraceChange = viewModel::setShowTrace,
            onKeepScreenOnChange = viewModel::setKeepScreenOn,
            onInputPolicyChange = viewModel::setInputPolicy,
            onPreferredInputChange = viewModel::setPreferredInput,
        ),
        modifier = modifier,
    )
}

/** Callbacks of [SettingsContent], bundled so the stateless screen stays readable. */
data class SettingsActions(
    val onA4Change: (Double) -> Unit,
    val onA4Step: (Int) -> Unit,
    val onA4Reset: () -> Unit,
    val onToleranceChange: (Double) -> Unit,
    val onNotationChange: (Notation) -> Unit,
    val onHeadstockChange: (HeadstockLayout) -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onShowHzChange: (Boolean) -> Unit,
    val onHapticsChange: (Boolean) -> Unit,
    val onShowTraceChange: (Boolean) -> Unit,
    val onKeepScreenOnChange: (Boolean) -> Unit,
    val onInputPolicyChange: (InputPolicy) -> Unit,
    val onPreferredInputChange: (String) -> Unit,
) {
    companion object {
        val None = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** Stateless body of [SettingsScreen]. */
@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val s = state.settings
    val haptics = LocalTunerHaptics.current
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            section("Tuning") {
                ReferencePitchRow(
                    a4Hz = s.a4Hz,
                    onChange = actions.onA4Change,
                    onStep = actions.onA4Step,
                    onReset = actions.onA4Reset,
                )
                ToleranceRow(toleranceCents = s.toleranceCents, onChange = actions.onToleranceChange)
                SegmentedRow(
                    title = "Notation",
                    options = Notation.entries,
                    selected = s.notation,
                    label = { if (it == Notation.SHARPS) "♯" else "♭" },
                    description = { if (it == Notation.SHARPS) "Sharps" else "Flats" },
                    onSelect = actions.onNotationChange,
                )
            }
            section("Display") {
                SegmentedRow(
                    title = "Headstock",
                    options = HeadstockLayout.entries,
                    selected = s.headstockLayout,
                    label = { it.label },
                    onSelect = actions.onHeadstockChange,
                )
                SegmentedRow(
                    title = "Theme",
                    options = ThemeMode.entries,
                    selected = s.theme,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    onSelect = actions.onThemeChange,
                )
                SwitchRow(
                    title = "Show frequency",
                    subtitle = "Detected pitch in Hz under the note",
                    checked = s.showHz,
                    onCheckedChange = actions.onShowHzChange,
                )
                SwitchRow(
                    title = "Needle trail",
                    subtitle = "A fading tail behind the needle, so you can watch a pluck settle",
                    checked = s.showTrace,
                    onCheckedChange = actions.onShowTraceChange,
                )
                SwitchRow(
                    title = "Haptics",
                    subtitle = "Ticks as you tune, a nudge when a string lands",
                    checked = s.haptics,
                    onCheckedChange = { on ->
                        if (on) haptics.demo()
                        actions.onHapticsChange(on)
                    },
                )
                SwitchRow(
                    title = "Keep screen on",
                    subtitle = "While the Tune screen is open",
                    checked = s.keepScreenOn,
                    onCheckedChange = actions.onKeepScreenOnChange,
                )
            }
            section("Input") {
                InputSection(
                    policy = s.inputPolicy,
                    preferredKey = s.preferredInputKey,
                    inputs = state.availableInputs,
                    onPolicyChange = actions.onInputPolicyChange,
                    onDeviceChange = actions.onPreferredInputChange,
                )
            }
            section("About") {
                val uriHandler = LocalUriHandler.current
                val haptics = LocalTunerHaptics.current
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text("Version ${state.appVersion.ifBlank { "\u2014" }}") },
                )
                ListItem(
                    headlineContent = { Text("Source code") },
                    supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        haptics.gestureEnd()
                        uriHandler.openUri(SOURCE_URL)
                    },
                )
                ListItem(
                    headlineContent = { Text("Licence") },
                    supportingContent = {
                        Text("GNU General Public License v3.0 \u2014 free to use, study, share and modify")
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        haptics.gestureEnd()
                        uriHandler.openUri(LICENCE_URL)
                    },
                )
                ListItem(
                    headlineContent = { Text("Pitch detection") },
                    supportingContent = { Text("YIN \u00b7 48 kHz") },
                )
                ListItem(
                    headlineContent = { Text("Typeface") },
                    supportingContent = { Text("Bricolage Grotesque by Mathieu Triay, SIL Open Font License 1.1") },
                )
                ListItem(
                    headlineContent = { Text("Made by") },
                    supportingContent = { Text("ntainy \u00b7 2026") },
                )
            }
        }
    }
}

private const val SOURCE_URL = "https://github.com/ntainy/open-guitar-tuner"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

private fun LazyListScope.section(title: String, content: @Composable () -> Unit) {
    item(key = "header_$title") {
        SectionHeader(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        )
    }
    item(key = "section_$title") { Column { content() } }
}

/**
 * A slider drag reports many values per whole step, so ticking on every callback would buzz. This ticks once
 * each time the *rounded* value changes, which is what the user sees change in the readout.
 */
@Composable
private fun rememberStepTicker(): (Int) -> Unit {
    val haptics = LocalTunerHaptics.current
    var last by remember { mutableIntStateOf(Int.MIN_VALUE) }
    return { step ->
        if (step != last) {
            last = step
            haptics.step()
        }
    }
}

// ---- Tuning ---------------------------------------------------------------------------------------------------

@Composable
private fun ReferencePitchRow(
    a4Hz: Double,
    onChange: (Double) -> Unit,
    onStep: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: a4Hz.roundToInt()
    val min = TunerSettings.MIN_A4_HZ.roundToInt()
    val max = TunerSettings.MAX_A4_HZ.roundToInt()
    val haptics = LocalTunerHaptics.current
    val tick = rememberStepTicker()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Reference pitch",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { haptics.step(); onStep(-1) }, enabled = shown > min) {
                Icon(Icons.Filled.Remove, contentDescription = "Lower reference pitch by 1 Hz")
            }
            Text(
                text = "$shown Hz",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 64.dp),
            )
            IconButton(onClick = { haptics.step(); onStep(+1) }, enabled = shown < max) {
                Icon(Icons.Filled.Add, contentDescription = "Raise reference pitch by 1 Hz")
            }
        }
        // A plain track: 51 tick marks read as a dotted line, and the stepper already shows the 1 Hz resolution.
        // The value is rounded as it moves instead, so the thumb still lands on whole hertz.
        Slider(
            value = dragging ?: a4Hz.toFloat(),
            onValueChange = {
                val whole = it.roundToInt()
                dragging = whole.toFloat()
                tick(whole)
            },
            onValueChangeFinished = {
                dragging?.let { onChange(it.roundToInt().toDouble()) }
                dragging = null
                haptics.gestureEnd()
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = 0,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Reference pitch, $shown hertz" },
        )
        TextButton(
            onClick = {
                haptics.confirm()
                onReset()
            },
            enabled = a4Hz.roundToInt() != 440,
        ) { Text("Reset to 440") }
    }
}

@Composable
private fun ToleranceRow(toleranceCents: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: toleranceCents.roundToInt()
    val min = TunerSettings.MIN_TOLERANCE_CENTS.roundToInt()
    val max = TunerSettings.MAX_TOLERANCE_CENTS.roundToInt()
    val haptics = LocalTunerHaptics.current
    val tick = rememberStepTicker()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "In-tune tolerance", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "The needle turns mint inside this window",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "±$shown cents",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragging ?: toleranceCents.toFloat(),
            onValueChange = {
                dragging = it
                tick(it.roundToInt())
            },
            onValueChangeFinished = {
                dragging?.let { onChange(it.roundToInt().toDouble()) }
                dragging = null
                haptics.gestureEnd()
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "In-tune tolerance, plus or minus $shown cents" },
        )
    }
}

// ---- Shared row types -----------------------------------------------------------------------------------------

@Composable
private fun <T> SegmentedRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: ((T) -> String)? = null,
) {
    // Two options sit beside the title; three or more get their own full-width line so nothing wraps on a 360 dp screen.
    val stacked = options.size >= 3
    val haptics = LocalTunerHaptics.current
    val buttons: @Composable () -> Unit = {
        SingleChoiceSegmentedButtonRow(modifier = if (stacked) Modifier.fillMaxWidth() else Modifier) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = {
                        if (option != selected) haptics.toggle(on = true)
                        onSelect(option)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                    label = { Text(label(option), maxLines = 1, softWrap = false) },
                    modifier = (if (stacked) Modifier else Modifier.widthIn(min = 96.dp)).let { base ->
                        if (description == null) base else {
                            val text = description(option)
                            base.semantics { contentDescription = text }
                        }
                    },
                )
            }
        }
    }
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            buttons()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            buttons()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalTunerHaptics.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            },
    )
}

@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val haptics = LocalTunerHaptics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton) {
                if (!selected) haptics.toggle(on = true)
                onClick()
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Input ----------------------------------------------------------------------------------------------------

@Composable
private fun InputSection(
    policy: InputPolicy,
    preferredKey: String?,
    inputs: List<AudioInputDevice>,
    onPolicyChange: (InputPolicy) -> Unit,
    onDeviceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        RadioRow(
            title = "Prefer USB interface",
            subtitle = "Falls back to the microphone when nothing is plugged in",
            selected = policy == InputPolicy.PREFER_USB,
            onClick = { onPolicyChange(InputPolicy.PREFER_USB) },
        )
        RadioRow(
            title = "Built-in microphone",
            selected = policy == InputPolicy.BUILTIN_MIC,
            onClick = { onPolicyChange(InputPolicy.BUILTIN_MIC) },
        )
        RadioRow(
            title = "Specific device",
            subtitle = inputs.firstOrNull { it.key == preferredKey }?.name,
            selected = policy == InputPolicy.SPECIFIC_DEVICE,
            onClick = { onPolicyChange(InputPolicy.SPECIFIC_DEVICE) },
        )
        if (policy == InputPolicy.SPECIFIC_DEVICE) {
            if (inputs.isEmpty()) {
                Text(
                    text = "No inputs detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }
            inputs.forEach { device ->
                RadioRow(
                    title = device.name,
                    selected = device.key == preferredKey,
                    onClick = { onDeviceChange(device.key) },
                    icon = device.kind.icon(),
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp, end = 16.dp))
        }
        UsbTips()
    }
}

/**
 * What someone plugging in an interface for the first time needs to know. Kept as prose rather than a help screen:
 * it is three sentences and this is the page they are already on when it does not work. Collapsed by default so
 * the Input section stays a list of choices; the row opens it in place.
 */
@Composable
private fun UsbTips(modifier: Modifier = Modifier, initiallyExpanded: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalTunerHaptics.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "usbTipsChevron",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Hide the USB tips" else "Show the USB tips",
                ) {
                    expanded = !expanded
                    haptics.toggle(on = expanded)
                }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Using a USB interface",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronTurn),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Connect the interface before opening the app \u2014 Android hands it over on connection, " +
                        "and a device plugged in later can take a moment to appear. \u201cPrefer USB\u201d picks it " +
                        "automatically and falls back to the microphone the moment it is unplugged, so you can " +
                        "leave it on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "If nothing shows up: check the cable carries data and not just power, make sure no other " +
                        "app is holding the interface, and reconnect it. Interfaces with their own amp modelling " +
                        "send that modelled signal, which tunes fine but is not the dry string.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "USB audio interfaces such as the NUX Mighty Plug Pro appear in the list above when " +
                        "connected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "If the tuner only reacts to hard strokes, the interface is quiet: tap the input icon on " +
                        "the Tune screen and raise Sensitivity until a softly played string clears the mark. The " +
                        "value is remembered for that interface.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun InputKind.icon(): ImageVector = when (this) {
    InputKind.BUILTIN_MIC -> Icons.Outlined.Mic
    InputKind.USB -> Icons.Outlined.Usb
    InputKind.WIRED_HEADSET -> Icons.Outlined.Headset
    InputKind.BLUETOOTH -> Icons.Outlined.Bluetooth
    InputKind.OTHER -> Icons.Outlined.SettingsInputComponent
    InputKind.TEST_TONE -> Icons.Outlined.GraphicEq
}

// ---- Previews -------------------------------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SettingsDefaultPreview() {
    GuitarTunerTheme {
        SettingsContent(
            state = SettingsUiState(availableInputs = PreviewData.inputs, appVersion = "0.1"),
            actions = SettingsActions.None,
        )
    }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun SettingsSpecificDevicePreview() {
    GuitarTunerTheme {
        SettingsContent(
            state = SettingsUiState(
                settings = TunerSettings(
                    a4Hz = 442.0,
                    toleranceCents = 5.0,
                    inputPolicy = InputPolicy.SPECIFIC_DEVICE,
                    preferredInputKey = PreviewData.usbInterface.key,
                    notation = Notation.FLATS,
                    headstockLayout = HeadstockLayout.SIX_IN_LINE,
                ),
                availableInputs = PreviewData.inputs,
                appVersion = "0.1",
            ),
            actions = SettingsActions.None,
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SettingsLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsUiState(
                settings = TunerSettings(theme = ThemeMode.LIGHT, showHz = false),
                availableInputs = PreviewData.inputs,
                appVersion = "0.1",
            ),
            actions = SettingsActions.None,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UsbTipsCollapsedPreview() {
    GuitarTunerTheme {
        Surface { UsbTips() }
    }
}

@Preview(showBackground = true)
@Composable
private fun UsbTipsExpandedPreview() {
    GuitarTunerTheme {
        Surface { UsbTips(initiallyExpanded = true) }
    }
}

@Preview(showBackground = true)
@Composable
private fun UsbTipsExpandedLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        Surface { UsbTips(initiallyExpanded = true) }
    }
}

/** Goes through the entry point and ViewModel with an in-memory container. */
@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SettingsViaContainerPreview() {
    GuitarTunerTheme {
        SettingsScreen(container = previewContainer())
    }
}
