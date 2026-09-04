package dev.ntainy.guitar_tuner.ui.tunings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme

/**
 * A tuning in the list: name, subtitle, six note chips, a brass check when selected (brass is the colour of the
 * thing you have chosen; mint is reserved for "in tune").
 *
 * Every row has an overflow menu. Custom rows offer Edit / Duplicate / Delete; preset rows offer
 * "Copy to My tunings", which a long-press on the row does as well.
 */
@Composable
fun TuningRow(
    row: TuningRowUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { selected = row.isSelected }
            .combinedClickable(
                onClick = onClick,
                onClickLabel = "Use this tuning",
                onLongClick = if (row.isCustom) null else onDuplicate,
                onLongClickLabel = "Copy to My tunings",
            )
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (row.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!row.isChromatic) NoteChipRow(labels = row.noteLabels)
        }
        if (row.isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${row.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (row.isCustom) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Copy to My tunings") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The chromatic mode, drawn as a row of its own above the tuning sections: a waveform icon where the others
 * have note chips, no menu (there is nothing to copy or edit), the same brass check when it is the active choice.
 */
@Composable
fun ChromaticRow(
    row: TuningRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = row.isSelected, role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            tint = if (row.isSelected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (row.isSelected) scheme.primary else scheme.onSurface,
                maxLines = 1,
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (row.isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = scheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

// ---- Previews -------------------------------------------------------------------------------------------------

private val previewCustomRow = TuningRowUi(
    id = "custom_a",
    name = "Open C (mine)",
    subtitle = "6th −4 · 5th −2 · 4th −2 · 2nd +1",
    noteLabels = listOf("C2", "G2", "C3", "G3", "C4", "E4"),
    isSelected = true,
    isCustom = true,
)

private val previewPresetRow = TuningRowUi(
    id = PresetIds.STANDARD,
    name = "Standard",
    subtitle = "The everyday tuning",
    noteLabels = listOf("E2", "A2", "D3", "G3", "B3", "E4"),
    isSelected = false,
    isCustom = false,
)

private fun previewChromaticRow(selected: Boolean) = TuningRowUi(
    id = PresetIds.CHROMATIC,
    name = "Chromatic",
    subtitle = "Any note, no strings",
    noteLabels = emptyList(),
    isSelected = selected,
    isCustom = false,
    isChromatic = true,
)

@Composable
private fun RowsPreview() {
    Surface {
        Column {
            ChromaticRow(row = previewChromaticRow(selected = false), onClick = {})
            TuningRow(row = previewCustomRow, onClick = {}, onEdit = {}, onDuplicate = {}, onDelete = {})
            TuningRow(row = previewPresetRow, onClick = {}, onEdit = {}, onDuplicate = {}, onDelete = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TuningRowPreview() {
    GuitarTunerTheme { RowsPreview() }
}

@Preview(showBackground = true)
@Composable
private fun TuningRowLightPreview() {
    GuitarTunerTheme(darkTheme = false) { RowsPreview() }
}

@Preview(showBackground = true)
@Composable
private fun ChromaticRowSelectedPreview() {
    GuitarTunerTheme {
        Surface {
            Column {
                ChromaticRow(row = previewChromaticRow(selected = true), onClick = {})
                TuningRow(
                    row = previewPresetRow,
                    onClick = {},
                    onEdit = {},
                    onDuplicate = {},
                    onDelete = {},
                )
            }
        }
    }
}
