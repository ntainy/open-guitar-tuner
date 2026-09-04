@file:OptIn(ExperimentalMaterial3Api::class)

package dev.ntainy.guitar_tuner.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.ui.theme.GuitarTunerTheme

/** Bottom sheet that lets the user pick any note from C1 to C6 for one string. */
@Composable
fun NotePickerSheet(
    title: String,
    current: Int,
    notation: Notation,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        NotePickerContent(
            title = title,
            current = current,
            notation = notation,
            onPick = onPick,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/** The list inside [NotePickerSheet], also used by previews since sheets render in their own window. */
@Composable
fun NotePickerContent(
    title: String,
    current: Int,
    notation: Notation,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes = remember { (MIN_STRING_MIDI..MAX_STRING_MIDI).toList() }
    val currentIndex = (current - MIN_STRING_MIDI).coerceIn(0, notes.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
    LaunchedEffect(current) { listState.scrollToItem((currentIndex - 3).coerceAtLeast(0)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .selectableGroup(),
        ) {
            items(notes, key = { it }) { midi ->
                val selected = midi == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(midi) })
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = NoteMath.name(midi, notation),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotePickerContentPreview() {
    GuitarTunerTheme {
        Surface {
            NotePickerContent(title = "6th string", current = 40, notation = Notation.SHARPS, onPick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotePickerContentFlatsLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        Surface {
            NotePickerContent(title = "2nd string", current = 58, notation = Notation.FLATS, onPick = {})
        }
    }
}
