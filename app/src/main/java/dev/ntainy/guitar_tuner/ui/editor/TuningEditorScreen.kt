@file:OptIn(ExperimentalMaterial3Api::class)

package dev.ntainy.guitar_tuner.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme
import dev.ntainy.guitar_tuner.ui.theme.farOff
import dev.ntainy.guitar_tuner.ui.tunings.NoteChipRow
import dev.ntainy.guitar_tuner.ui.tunings.PreviewData
import dev.ntainy.guitar_tuner.ui.tunings.previewContainer

/** Creates or edits a custom tuning; [onDone] is called on Save, Close and the system back gesture. */
@Composable
fun TuningEditorScreen(container: AppContainer, tuningId: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TuningEditorViewModel = viewModel(
        key = "editor:$tuningId",
        factory = viewModelFactory {
            initializer { TuningEditorViewModel(container.tuningsRepository, container.settingsRepository, tuningId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TuningEditorEvent.Saved -> currentOnDone()
            }
        }
    }
    BackHandler(onBack = onDone)

    TuningEditorContent(
        state = state,
        onNameChange = viewModel::setName,
        onNudge = viewModel::nudge,
        onSetNote = viewModel::setNote,
        onShiftAll = viewModel::shiftAll,
        onSave = viewModel::save,
        onClose = onDone,
        modifier = modifier,
    )
}

/** Stateless body of [TuningEditorScreen]; the only local state is which string's note picker is open. */
@Composable
fun TuningEditorContent(
    state: TuningEditorUiState,
    onNameChange: (String) -> Unit,
    onNudge: (index: Int, delta: Int) -> Unit,
    onSetNote: (index: Int, midi: Int) -> Unit,
    onShiftAll: (delta: Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialPickerIndex: Int? = null,
) {
    val haptics = LocalTunerHaptics.current
    var pickerIndex by rememberSaveable { mutableStateOf(initialPickerIndex) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New tuning" else "Edit tuning") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close without saving")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            haptics.confirm()
                            onSave()
                        },
                        enabled = state.canSave,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                enabled = state.isLoaded,
                isError = state.errors.isNotEmpty(),
                supportingText = if (state.errors.isEmpty()) null else {
                    {
                        Column {
                            state.errors.forEach { error ->
                                Text(text = error, color = MaterialTheme.colorScheme.farOff)
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            NoteChipRow(labels = state.noteLabels)
            Spacer(Modifier.height(12.dp))

            state.notes.forEachIndexed { index, midi ->
                StringRow(
                    label = STRING_LABELS[index],
                    noteLabel = state.noteLabels[index],
                    canLower = state.isLoaded && midi > MIN_STRING_MIDI,
                    canRaise = state.isLoaded && midi < MAX_STRING_MIDI,
                    onLower = { onNudge(index, -1) },
                    onRaise = { onNudge(index, +1) },
                    onPick = { pickerIndex = index },
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { haptics.step(); onShiftAll(-1) },
                    enabled = state.isLoaded && state.canShiftDown,
                    modifier = Modifier.weight(1f),
                ) { Text("Shift all −1") }
                OutlinedButton(
                    onClick = { haptics.step(); onShiftAll(+1) },
                    enabled = state.isLoaded && state.canShiftUp,
                    modifier = Modifier.weight(1f),
                ) { Text("Shift all +1") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pickerIndex?.let { index ->
        NotePickerSheet(
            title = "${STRING_LABELS[index]} string",
            current = state.notes[index],
            notation = state.notation,
            onPick = { midi ->
                onSetNote(index, midi)
                pickerIndex = null
            },
            onDismiss = { pickerIndex = null },
        )
    }
}

@Composable
private fun StringRow(
    label: String,
    noteLabel: String,
    canLower: Boolean,
    canRaise: Boolean,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalTunerHaptics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { haptics.step(); onLower() }, enabled = canLower) {
            Icon(Icons.Filled.Remove, contentDescription = "Lower $label a semitone")
        }
        Text(
            text = noteLabel,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 72.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onPick, onClickLabel = "Choose the note for $label")
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
        IconButton(onClick = { haptics.step(); onRaise() }, enabled = canRaise) {
            Icon(Icons.Filled.Add, contentDescription = "Raise $label a semitone")
        }
    }
}

// ---- Previews -------------------------------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun EditorNewPreview() {
    GuitarTunerTheme {
        TuningEditorContent(
            state = TuningEditorUiState(name = "New tuning", isNew = true, isLoaded = true),
            onNameChange = {},
            onNudge = { _, _ -> },
            onSetNote = { _, _ -> },
            onShiftAll = {},
            onSave = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun EditorEditingWithErrorPreview() {
    GuitarTunerTheme {
        TuningEditorContent(
            state = TuningEditorUiState(
                name = "",
                notes = PreviewData.customOpenC.strings,
                errors = listOf("Name is empty"),
                isNew = false,
                isLoaded = true,
            ),
            onNameChange = {},
            onNudge = { _, _ -> },
            onSetNote = { _, _ -> },
            onShiftAll = {},
            onSave = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun EditorFlatsLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        TuningEditorContent(
            state = TuningEditorUiState(
                name = "Half step down",
                notes = PreviewData.eFlat.strings,
                notation = Notation.FLATS,
                isNew = false,
                isLoaded = true,
            ),
            onNameChange = {},
            onNudge = { _, _ -> },
            onSetNote = { _, _ -> },
            onShiftAll = {},
            onSave = {},
            onClose = {},
        )
    }
}

/** Goes through the entry point and ViewModel, editing an existing custom tuning from the preview container. */
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun EditorViaContainerPreview() {
    GuitarTunerTheme {
        TuningEditorScreen(container = previewContainer(), tuningId = PreviewData.customOpenC.id, onDone = {})
    }
}
