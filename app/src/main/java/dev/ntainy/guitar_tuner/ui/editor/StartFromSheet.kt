@file:OptIn(ExperimentalMaterial3Api::class)

package dev.ntainy.guitar_tuner.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme
import dev.ntainy.guitar_tuner.ui.tunings.NoteChipRow
import dev.ntainy.guitar_tuner.ui.tunings.PreviewData
import dev.ntainy.guitar_tuner.ui.tunings.SectionHeader
import dev.ntainy.guitar_tuner.ui.tunings.TuningRowUi
import dev.ntainy.guitar_tuner.ui.tunings.TuningSectionUi

/** Bottom sheet listing every tuning the six notes can be copied from; picking one calls [onPick] with its id. */
@Composable
fun StartFromSheet(
    sections: List<TuningSectionUi>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        StartFromContent(
            sections = sections,
            onPick = onPick,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/** The list inside [StartFromSheet], also used by previews since sheets render in their own window. */
@Composable
fun StartFromContent(
    sections: List<TuningSectionUi>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalTunerHaptics.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Start from",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = "Copies the six notes; the name stays yours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            sections.forEach { section ->
                item(key = "header_${section.group.name}") {
                    SectionHeader(
                        text = section.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                items(section.rows, key = { it.id }) { row ->
                    TemplateRow(
                        row = row,
                        onClick = {
                            haptics.confirm()
                            onPick(row.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(row: TuningRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = "Start from ${row.name}")
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (row.subtitle.isNotBlank()) {
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        NoteChipRow(labels = row.noteLabels)
    }
}

// ---- Previews -------------------------------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun StartFromContentPreview() {
    GuitarTunerTheme {
        Surface {
            StartFromContent(sections = buildTemplates(PreviewData.withCustom, Notation.SHARPS), onPick = {})
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun StartFromContentPresetsOnlyLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        Surface {
            StartFromContent(sections = buildTemplates(PreviewData.presets, Notation.FLATS), onPick = {})
        }
    }
}
