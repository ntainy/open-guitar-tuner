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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntainy.guitar_tuner.R
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.ui.theme.target
import kotlinx.coroutines.launch

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
                    referenceTone = container.referenceTonePlayer,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trace by viewModel.trace.collectAsStateWithLifecycle()
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
    val haptics = LocalTunerHaptics.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel, haptics) {
        viewModel.events.collect { event ->
            when (event) {
                // Showing the snackbar suspends until it is dismissed, so it goes on its own coroutine:
                // the strings you keep tuning while it is up must still be felt.
                TunerEvent.AllTuned -> {
                    haptics.allTuned()
                    scope.launch { snackbarHost.showSnackbar(ALL_SET_MESSAGE, duration = SnackbarDuration.Short) }
                }
                TunerEvent.StringTuned -> haptics.confirm()
                TunerEvent.EnteredBand -> haptics.enterBand()
            }
        }
    }

    TunerContent(
        state = state,
        trace = trace,
        permanentlyDenied = permanentlyDenied,
        snackbarHost = snackbarHost,
        onOpenTunings = onOpenTunings,
        onSelectString = { index ->
            // The ViewModel decides pin vs. hand-back-to-AUTO; the feel has to agree with what will happen.
            val unpinning = !state.autoMode && state.strings.getOrNull(index)?.isTarget == true
            haptics.toggle(on = !unpinning)
            viewModel.onStringTap(index)
        },
        onStringLongPress = viewModel::playReferenceTone,
        onAutoMode = { enabled ->
            haptics.toggle(enabled)
            viewModel.setAutoMode(enabled)
        },
        onStartOver = {
            haptics.gestureEnd()
            viewModel.startOver()
        },
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
    trace: List<TracePoint> = emptyList(),
    onOpenTunings: () -> Unit,
    onSelectString: (Int) -> Unit,
    onAutoMode: (Boolean) -> Unit,
    onStartOver: () -> Unit,
    onOpenInput: () -> Unit,
    onAllowMic: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onStringLongPress: ((Int) -> Unit)? = null,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(
        targetValue = needleColor(state.centsOff, state.inTune, scheme),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "noteRing",
    )

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
                showAutoMode = !state.chromatic,
                input = state.input,
                inputCaption = state.inputCaption,
                onOpenInput = onOpenInput,
            )
            TuningRow(
                tuningName = state.tuningName,
                instrumentLabel = if (state.chromatic) null else "Guitar 6-string",
                onOpenTunings = onOpenTunings,
            )
            if (!state.permissionGranted) {
                MicPermissionGate(
                    permanentlyDenied = permanentlyDenied,
                    onAllow = onAllowMic,
                    onOpenSettings = onOpenAppSettings,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                TunerBody(
                    state = state,
                    trace = trace,
                    ringColor = ringColor,
                    onSelectString = onSelectString,
                    onStringLongPress = onStringLongPress,
                    onStartOver = onStartOver,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
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

/** Below this the trace is dropped so the headstock keeps its room. */
private val TRACE_MIN_BODY_HEIGHT = 420.dp

/**
 * Gauge, note and headstock. Stacked on a phone held upright; side by side once the screen is wider than it is
 * tall, which is landscape and every tablet — there the headstock finally gets the height a 6-in-line column wants.
 */
@Composable
private fun TunerBody(
    state: TunerUiState,
    trace: List<TracePoint>,
    ringColor: Color,
    onSelectString: (Int) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
    onStringLongPress: ((Int) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val sideBySide = maxWidth > maxHeight
        // The trace is the first thing to go on a short phone: the headstock needs that height more.
        val showTrace = trace.isNotEmpty() && (sideBySide || maxHeight >= TRACE_MIN_BODY_HEIGHT)
        val gauge: @Composable ColumnScope.() -> Unit = {
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
            if (showTrace) {
                Spacer(Modifier.height(8.dp))
                PitchTrace(
                    points = trace,
                    toleranceCents = state.toleranceCents,
                    // The newest sample sits on the right edge, so it also serves as "now".
                    nowMs = trace.last().atMs,
                )
            }
        }
        val startOver: @Composable () -> Unit = {
            // Chromatic mode never earns tuned marks, so there is nothing to start over from.
            if (!state.chromatic) {
                FilledTonalButton(onClick = onStartOver, enabled = state.anyTuned) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start over")
                }
            }
        }
        val headstock: @Composable (Modifier) -> Unit = { headstockModifier ->
            if (state.chromatic) {
                Box(modifier = headstockModifier, contentAlignment = Alignment.Center) {
                    NoteStrip(
                        midi = state.chromaticMidi,
                        notation = state.notation,
                        inTune = state.inTune,
                    )
                }
            } else {
                Headstock(
                    layout = state.headstockLayout,
                    strings = state.strings,
                    onStringTap = onSelectString,
                    modifier = headstockModifier,
                    manualMode = !state.autoMode,
                    onStringLongPress = onStringLongPress,
                )
            }
        }

        if (sideBySide) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    gauge()
                    Spacer(Modifier.height(16.dp))
                    startOver()
                }
                headstock(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                gauge()
                headstock(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp))
                Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { startOver() }
            }
        }
    }
}

@Composable
private fun TunerHeader(
    autoMode: Boolean,
    onAutoMode: (Boolean) -> Unit,
    showAutoMode: Boolean,
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
            text = stringResource(R.string.app_name),
            // titleMedium, not titleLarge: "OpenGuitarTuner" is four characters longer than the old name and
            // ellipsises at titleLarge once the AUTO switch and the input button have taken their share of the row.
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Chromatic mode has no strings, so there is nothing for AUTO to choose between.
        if (showAutoMode) {
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
private fun TuningRow(tuningName: String, instrumentLabel: String?, onOpenTunings: () -> Unit) {
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
        // Chromatic is not an instrument's tuning, so it stands alone instead of hanging off a breadcrumb.
        if (instrumentLabel != null) {
            Text(text = instrumentLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(text = "›", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
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
