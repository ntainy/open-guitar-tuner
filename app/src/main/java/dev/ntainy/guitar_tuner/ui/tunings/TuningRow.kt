package dev.ntainy.guitar_tuner.ui.tunings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme
import dev.ntainy.guitar_tuner.ui.theme.inTune

/**
 * A tuning in the list: name, subtitle, six note chips, a mint check when selected.
 * Custom rows get an overflow menu (Edit / Duplicate / Delete); preset rows copy themselves on long-press.
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
            .padding(start = 16.dp, end = if (row.isCustom) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
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
                tint = MaterialTheme.colorScheme.inTune,
                modifier = Modifier.padding(start = 12.dp, end = if (row.isCustom) 0.dp else 4.dp),
            )
        }
        if (row.isCustom) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${row.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TuningRowPreview() {
    GuitarTunerTheme {
        Surface {
            Column {
                TuningRow(
                    row = TuningRowUi(
                        id = "custom_a",
                        name = "Open C (mine)",
                        subtitle = "C G C G C E",
                        noteLabels = listOf("C2", "G2", "C3", "G3", "C4", "E4"),
                        isSelected = true,
                        isCustom = true,
                    ),
                    onClick = {},
                    onEdit = {},
                    onDuplicate = {},
                    onDelete = {},
                )
                TuningRow(
                    row = TuningRowUi(
                        id = "preset_standard",
                        name = "Standard",
                        subtitle = "E A D G B E",
                        noteLabels = listOf("E2", "A2", "D3", "G3", "B3", "E4"),
                        isSelected = false,
                        isCustom = false,
                    ),
                    onClick = {},
                    onEdit = {},
                    onDuplicate = {},
                    onDelete = {},
                )
            }
        }
    }
}
