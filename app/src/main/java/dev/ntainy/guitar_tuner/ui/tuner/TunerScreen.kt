package dev.ntainy.guitar_tuner.ui.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntainy.guitar_tuner.audio.AudioInputDevice
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.inTuneContainer
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
            onSensitivityDb = viewModel::setSensitivityDb,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .vignette(inner = scheme.surfaceContainerLow, outer = scheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TunerHeader(
                tuningName = state.tuningName,
                tuningNotes = state.tuningNotes,
                onOpenTunings = onOpenTunings,
                autoMode = state.autoMode,
                onAutoMode = onAutoMode,
                showAutoMode = !state.chromatic,
                input = state.input,
                onOpenInput = onOpenInput,
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

/**
 * The readout over the headstock, which takes everything that is left. Stacked on a phone held upright; side by
 * side once the screen is wider than it is tall, which is landscape and every tablet — there the readout sits
 * centred on the left and the headstock gets the full height on the right.
 */
@Composable
private fun TunerBody(
    state: TunerUiState,
    trace: List<TracePoint>,
    onSelectString: (Int) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
    onStringLongPress: ((Int) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val sideBySide = maxWidth > maxHeight
        val readout: @Composable (Modifier) -> Unit = { readoutModifier ->
            Readout(
                centsOff = state.centsOff,
                centsLabel = state.centsLabel,
                inTune = state.inTune,
                hint = state.hint,
                detail = state.readoutDetail,
                toleranceCents = state.toleranceCents,
                trail = trace,
                modifier = readoutModifier,
            )
        }
        val instrument: @Composable (Modifier) -> Unit = { instrumentModifier ->
            if (state.chromatic) {
                ChromaticBlock(state = state, modifier = instrumentModifier)
            } else {
                HeadstockBlock(
                    state = state,
                    onSelectString = onSelectString,
                    onStringLongPress = onStringLongPress,
                    onStartOver = onStartOver,
                    modifier = instrumentModifier,
                )
            }
        }

        if (sideBySide) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Scrollable only as a safety net: at 150 dp the readout fits any landscape phone.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    readout(Modifier.fillMaxWidth())
                }
                instrument(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(8.dp))
                readout(Modifier.fillMaxWidth())
                instrument(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, bottom = 4.dp))
            }
        }
    }
}

/** The headstock with its buttons, and "Start over" tucked into the bottom-end corner once a mark has been earned. */
@Composable
private fun HeadstockBlock(
    state: TunerUiState,
    onSelectString: (Int) -> Unit,
    onStringLongPress: ((Int) -> Unit)?,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Headstock(
            layout = state.headstockLayout,
            strings = state.strings,
            onStringTap = onSelectString,
            modifier = Modifier.fillMaxSize(),
            manualMode = !state.autoMode,
            onStringLongPress = onStringLongPress,
        )
        AnimatedVisibility(
            visible = state.anyTuned,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f),
        ) {
            TextButton(onClick = onStartOver) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start over")
            }
        }
    }
}

/**
 * What stands in for the headstock in chromatic mode: the note being heard, large, over the twelve-note strip.
 * The glyph gives up size before the strip does, so both fit a landscape phone.
 */
@Composable
private fun ChromaticBlock(state: TunerUiState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(
        targetValue = readingColor(state.centsOff, state.inTune, scheme),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "noteRing",
    )
    BoxWithConstraints(modifier = modifier) {
        val glyphSize = (maxHeight - CHROMATIC_STRIP_ROOM).coerceIn(NOTE_GLYPH_SIZE, CHROMATIC_GLYPH_SIZE)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NoteGlyph(
                letter = state.targetLetter,
                octave = state.targetOctave,
                ringColor = ringColor,
                inTune = state.inTune,
                size = glyphSize,
            )
            Spacer(Modifier.height(24.dp))
            NoteStrip(
                midi = state.chromaticMidi,
                notation = state.notation,
                inTune = state.inTune,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the strip and the gap above it need, so the glyph knows how much height is really its own. */
private val CHROMATIC_STRIP_ROOM = 96.dp

@Composable
private fun TunerHeader(
    tuningName: String,
    tuningNotes: String,
    onOpenTunings: () -> Unit,
    autoMode: Boolean,
    onAutoMode: (Boolean) -> Unit,
    showAutoMode: Boolean,
    input: AudioInputDevice?,
    onOpenInput: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TuningChip(name = tuningName, notes = tuningNotes, onClick = onOpenTunings)
        }
        // Chromatic mode has no strings, so there is nothing for AUTO to choose between.
        if (showAutoMode) AutoChip(autoMode = autoMode, onAutoMode = onAutoMode)
        IconButton(onClick = onOpenInput) {
            Icon(
                imageVector = input?.kind?.icon ?: Icons.Outlined.MicOff,
                contentDescription = "Input: ${input?.name ?: "none"}",
                tint = if (input == null) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
    }
}

/**
 * The active tuning as a pill: its name, then its six pitch letters, then a chevron that says it opens a list.
 * In chromatic mode there are no letters and the pill just reads "Chromatic".
 */
@Composable
private fun TuningChip(name: String, notes: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = "Choose tuning")
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (notes.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.08.em),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** AUTO as an outlined pill that lights brass while the tuner picks the string itself. */
@Composable
private fun AutoChip(autoMode: Boolean, onAutoMode: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val border by animateColorAsState(
        targetValue = if (autoMode) scheme.target else scheme.outline,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "autoBorder",
    )
    val text by animateColorAsState(
        targetValue = if (autoMode) scheme.target else scheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "autoText",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.5.dp, border, shape)
            .toggleable(value = autoMode, role = Role.Switch, onValueChange = onAutoMode)
            .semantics { contentDescription = "Auto string detection" }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "AUTO", style = MaterialTheme.typography.labelLarge, color = text)
    }
}

/** Faint radial vignette: a touch lighter around the readout, falling off to the plain background. */
private fun Modifier.vignette(inner: Color, outer: Color): Modifier = drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(inner, outer),
            center = Offset(size.width / 2f, size.height * 0.3f),
            radius = maxOf(size.width, size.height) * 0.75f,
        ),
    )
}
