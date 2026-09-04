@file:OptIn(ExperimentalMaterial3Api::class)

package dev.ntainy.guitar_tuner.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.ui.haptics.LocalTunerHaptics
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

/**
 * The picker inside [NotePickerSheet], also used by previews since sheets render in their own window.
 *
 * Two steps instead of sixty rows: the note as twelve pitch-class chips, the octave as a segmented row, and the
 * result written large at the top. Combinations outside C1..C6 are disabled rather than hidden, so the layout
 * never shifts. [onPick] fires once, from Done.
 */
@Composable
fun NotePickerContent(
    title: String,
    current: Int,
    notation: Notation,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalTunerHaptics.current
    val start = current.coerceIn(MIN_STRING_MIDI, MAX_STRING_MIDI)
    var pitchClass by rememberSaveable(start) { mutableStateOf(NoteMath.pitchClass(start)) }
    var octave by rememberSaveable(start) { mutableStateOf(NoteMath.octave(start)) }
    val midi = NotePickerRange.midi(pitchClass, octave)
    val noteName = NoteMath.name(midi, notation)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = noteName,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Chosen note $noteName" },
        )

        PickerLabel("Note")
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotePickerRange.pitchClasses.chunked(PITCH_CLASSES_PER_ROW).forEach { rowClasses ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowClasses.forEach { pc ->
                        PitchClassChip(
                            label = NoteMath.letter(pc, notation),
                            selected = pc == pitchClass,
                            enabled = NotePickerRange.isAllowed(pc, octave),
                            onClick = {
                                if (pc != pitchClass) haptics.toggle(on = true)
                                pitchClass = pc
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        PickerLabel("Octave")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val octaves = NotePickerRange.octaves.toList()
            octaves.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == octave,
                    onClick = {
                        if (option != octave) haptics.toggle(on = true)
                        octave = option
                    },
                    enabled = NotePickerRange.isAllowed(pitchClass, option),
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = octaves.size),
                    icon = {},
                    label = { Text(option.toString(), maxLines = 1, softWrap = false) },
                )
            }
        }

        Button(
            onClick = {
                haptics.confirm()
                onPick(midi)
            },
            enabled = NotePickerRange.isAllowed(midi),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text("Done") }
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** One of the twelve note pills: brass when chosen, dimmed when the current octave has no such note. */
@Composable
private fun PitchClassChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = when {
            selected -> scheme.primary
            enabled -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLow
        },
        contentColor = when {
            selected -> scheme.onPrimary
            enabled -> scheme.onSurface
            else -> scheme.onSurface.copy(alpha = DISABLED_ALPHA)
        },
        modifier = modifier.height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

private const val PITCH_CLASSES_PER_ROW = 6
private const val DISABLED_ALPHA = 0.38f

// ---- Previews -------------------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun NotePickerContentPreview() {
    GuitarTunerTheme {
        Surface {
            NotePickerContent(
                title = "6th (low E) string",
                current = 40,
                notation = Notation.SHARPS,
                onPick = {},
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotePickerContentFlatsLightPreview() {
    GuitarTunerTheme(darkTheme = false) {
        Surface {
            NotePickerContent(
                title = "2nd string",
                current = 58,
                notation = Notation.FLATS,
                onPick = {},
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

/** The top of the range: in octave 6 only C is allowed, so eleven chips are dimmed. */
@Preview(showBackground = true)
@Composable
private fun NotePickerContentTopOfRangePreview() {
    GuitarTunerTheme {
        Surface {
            NotePickerContent(
                title = "1st (high E) string",
                current = MAX_STRING_MIDI,
                notation = Notation.SHARPS,
                onPick = {},
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
