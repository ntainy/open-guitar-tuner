package dev.ntainy.guitar_tuner.ui.tunings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme

/** A small rounded tag with a note name ("E2"), tabular digits so a row of six lines up. */
@Composable
fun NoteChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

/** Six chips in a row, low string first. */
@Composable
fun NoteChipRow(labels: List<String>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { NoteChip(label = it) }
    }
}

@Preview(showBackground = true)
@Composable
private fun NoteChipRowPreview() {
    GuitarTunerTheme {
        Surface {
            NoteChipRow(labels = listOf("E2", "A2", "D3", "G3", "B3", "E4"), modifier = Modifier.padding(16.dp))
        }
    }
}
