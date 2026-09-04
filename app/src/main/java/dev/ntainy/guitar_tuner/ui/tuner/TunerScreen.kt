package dev.ntainy.guitar_tuner.ui.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.inTuneContainer
import androidx.compose.runtime.remember
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.ui.theme.target

private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

/**
 * The Tune screen. Owns the ViewModel, the RECORD_AUDIO request, the engine lifecycle (runs only while resumed) and
 * keep-screen-on; everything visible is the stateless [TunerContent].
 */
@Composable
fun TunerScreen(container: AppContainer, onOpenTunings: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TunerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TunerViewModel(
                    engine = container.tunerEngine,
                    settings = container.settingsRepository,
                    activeTuning = container.activeTuning,
                    testTone = container.testToneFrequencyHz,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
        // After a denial, no rationale means the system will not ask again: send the user to app settings.
        permanentlyDenied = !granted && activity?.shouldShowRequestPermissionRationale(MIC_PERMISSION) == false
    }
    LaunchedEffect(Unit) {
        val granted = context.hasMicPermission()
        viewModel.onPermissionResult(granted)
        if (!granted && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(MIC_PERMISSION)
        }
    }

    // Capture runs only while this screen is resumed; re-keyed so a fresh grant restarts it.
    LifecycleResumeEffect(state.permissionGranted) {
        val granted = context.hasMicPermission()
        if (granted != state.permissionGranted) viewModel.onPermissionResult(granted)
        viewModel.start()
        onPauseOrDispose { viewModel.stop() }
    }

    val view = LocalView.current
    DisposableEffect(view, state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var showInputSheet by rememberSaveable { mutableStateOf(false) }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TunerEvent.AllTuned -> snackbarHost.showSnackbar(ALL_SET_MESSAGE, duration = SnackbarDuration.Short)
            }
        }
    }

    TunerContent(
        state = state,
        permanentlyDenied = permanentlyDenied,
        snackbarHost = snackbarHost,
        onOpenTunings = onOpenTunings,
        onSelectString = viewModel::onStringTap,
        onAutoMode = viewModel::setAutoMode,
        onStartOver = viewModel::startOver,
        onOpenInput = { showInputSheet = true },
        onAllowMic = { permissionLauncher.launch(MIC_PERMISSION) },
        onOpenAppSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        modifier = modifier,
    )

    if (showInputSheet) {
        InputPickerSheet(
            state = state,
            onSelectInput = viewModel::selectInput,
            onTestToneHz = viewModel::setTestToneHz,
            onDismiss = { showInputSheet = false },
        )
    }
}

private fun android.content.Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, MIC_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** Stateless body of the Tune screen; previews render this directly. */
@Composable
fun TunerContent(
    state: TunerUiState,
    permanentlyDenied: Boolean,
    onOpenTunings: () -> Unit,
    onSelectString: (Int) -> Unit,
    onAutoMode: (Boolean) -> Unit,
    onStartOver: () -> Unit,
    onOpenInput: () -> Unit,
    onAllowMic: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(needleColor(state.centsOff, state.inTune, scheme), label = "noteRing")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .vignette(inner = scheme.surfaceContainerLow, outer = scheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TunerHeader(
                autoMode = state.autoMode,
                onAutoMode = onAutoMode,
                input = state.input,
                inputCaption = state.inputCaption,
                onOpenInput = onOpenInput,
            )
            TuningRow(tuningName = state.tuningName, onOpenTunings = onOpenTunings)
            if (!state.permissionGranted) {
                MicPermissionGate(
                    permanentlyDenied = permanentlyDenied,
                    onAllow = onAllowMic,
                    onOpenSettings = onOpenAppSettings,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                CentsGauge(
                    centsOff = state.centsOff,
                    inTune = state.inTune,
                    toleranceCents = state.toleranceCents,
                    hint = state.hint,
                    centsLabel = state.centsLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                NoteGlyph(
                    letter = state.targetLetter,
                    octave = state.targetOctave,
                    ringColor = ringColor,
                    inTune = state.inTune,
                    pitchLabel = if (state.showHz) state.pitchHz?.let(::formatHz) else null,
                    showPitchSlot = state.showHz,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Headstock(
                    layout = state.headstockLayout,
                    strings = state.strings,
                    onStringTap = onSelectString,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                    manualMode = !state.autoMode,
                )
                FilledTonalButton(
                    onClick = onStartOver,
                    enabled = state.anyTuned,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start over")
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        ) { data ->
            Snackbar(containerColor = scheme.inTuneContainer, contentColor = scheme.inTune) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(data.visuals.message)
                }
            }
        }
    }
}

private const val ALL_SET_MESSAGE = "All six strings in tune. You're all set!"

@Composable
private fun TunerHeader(
    autoMode: Boolean,
    onAutoMode: (Boolean) -> Unit,
    input: AudioInputDevice?,
    inputCaption: String,
    onOpenInput: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GuitarTuner",
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .toggleable(value = autoMode, role = Role.Switch, onValueChange = onAutoMode)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "AUTO",
                style = MaterialTheme.typography.labelLarge,
                color = if (autoMode) scheme.target else scheme.onSurfaceVariant,
            )
            Switch(checked = autoMode, onCheckedChange = null)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onOpenInput) {
                Icon(
                    imageVector = input?.kind?.icon ?: Icons.Outlined.MicOff,
                    contentDescription = "Input: ${input?.name ?: "none"}",
                    tint = if (input == null) scheme.onSurfaceVariant else scheme.onSurface,
                )
            }
            Text(
                text = inputCaption,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun TuningRow(tuningName: String, onOpenTunings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenTunings, role = Role.Button, onClickLabel = "Choose tuning")
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "Guitar 6-string", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Text(text = "›", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Text(
            text = tuningName,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Faint radial vignette: a touch lighter around the gauge, falling off to the plain background. */
private fun Modifier.vignette(inner: Color, outer: Color): Modifier = drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(inner, outer),
            center = Offset(size.width / 2f, size.height * 0.3f),
            radius = maxOf(size.width, size.height) * 0.75f,
        ),
    )
}
