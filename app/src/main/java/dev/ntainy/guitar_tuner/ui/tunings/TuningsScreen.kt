@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.ntainy.guitar_tuner.ui.tunings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme

/** Grouped list of tunings: My tunings first, then the preset groups. Tap selects, + opens the editor. */
@Composable
fun TuningsScreen(container: AppContainer, onEditTuning: (String?) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TuningsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TuningsViewModel(container.tuningsRepository, container.settingsRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                TuningsMessage.Copied -> snackbarHostState.showSnackbar("Copied to My tunings")
                is TuningsMessage.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(message)
                }
            }
        }
    }

    TuningsContent(
        state = state,
        onSelect = viewModel::select,
        onEdit = onEditTuning,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
        onLayoutChange = viewModel::setHeadstockLayout,
        onAdd = { onEditTuning(null) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Stateless body of [TuningsScreen]. */
@Composable
fun TuningsContent(
    state: TuningsUiState,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLayoutChange: (HeadstockLayout) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var pendingDelete by remember { mutableStateOf<TuningRowUi?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(title = { Text("Tunings") })
                HeadstockLayoutSelector(
                    layout = state.headstockLayout,
                    onLayoutChange = onLayoutChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "New tuning")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            state.sections.forEach { section ->
                stickyHeader(key = "header_${section.group.name}") {
                    SectionHeader(
                        text = section.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                if (section.group == TuningGroup.MINE && section.rows.isEmpty()) {
                    item(key = "empty_mine") { EmptyMineRow() }
                }
                items(section.rows, key = { it.id }) { row ->
                    TuningRow(
                        row = row,
                        onClick = { onSelect(row.id) },
                        onEdit = { onEdit(row.id) },
                        onDuplicate = { onDuplicate(row.id) },
                        onDelete = { pendingDelete = row },
                    )
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${row.name}”?") },
            text = { Text("This removes the tuning from My tunings. You can undo right after.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(row.id)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HeadstockLayoutSelector(
    layout: HeadstockLayout,
    onLayoutChange: (HeadstockLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Headstock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        SingleChoiceSegmentedButtonRow {
            HeadstockLayout.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == layout,
                    onClick = { onLayoutChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = HeadstockLayout.entries.size),
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun EmptyMineRow(modifier: Modifier = Modifier) {
    Text(
        text = "No custom tunings yet — tap + to add one",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

// ---- Previews -------------------------------------------------------------------------------------------------

private fun previewState(all: List<Tuning>, activeId: String) =
    TuningsUiState(sections = buildSections(all, activeId, Notation.SHARPS))

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun TuningsListPreview() {
    GuitarTunerTheme {
        TuningsContent(
            state = previewState(PreviewData.withCustom, PreviewData.customOpenC.id),
            onSelect = {},
            onEdit = {},
            onDuplicate = {},
            onDelete = {},
            onLayoutChange = {},
            onAdd = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun TuningsListEmptyMinePreview() {
    GuitarTunerTheme {
        TuningsContent(
            state = previewState(PreviewData.presets, PreviewData.standard.id),
            onSelect = {},
            onEdit = {},
            onDuplicate = {},
            onDelete = {},
            onLayoutChange = {},
            onAdd = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun TuningsListLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        TuningsContent(
            state = previewState(PreviewData.withCustom, PreviewData.dropD.id)
                .copy(headstockLayout = HeadstockLayout.SIX_IN_LINE),
            onSelect = {},
            onEdit = {},
            onDuplicate = {},
            onDelete = {},
            onLayoutChange = {},
            onAdd = {},
        )
    }
}

/** Goes through the real entry point and ViewModel with an in-memory container. */
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun TuningsScreenViaContainerPreview() {
    GuitarTunerTheme {
        TuningsScreen(
            container = previewContainer(settings = TunerSettings(activeTuningId = PreviewData.customBaritone.id)),
            onEditTuning = {},
        )
    }
}
