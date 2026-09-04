package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.dsp.NoteMath
import dev.ntainy.guitar_tuner.dsp.Notation
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.target

/** The twelve pitch classes, in order from C. */
private const val PITCH_CLASSES = 12

/**
 * What the headstock becomes in chromatic mode: the twelve notes of the scale, with the one being heard lit.
 *
 * Nothing here is tappable — chromatic mode has no string to pin — so this is a readout, not a control. The
 * octave is shown separately underneath, because the pitch class is what you read at a glance and the octave is
 * what you check afterwards.
 */
@Composable
fun NoteStrip(
    midi: Int?,
    notation: Notation,
    inTune: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val current = midi?.let { NoteMath.pitchClass(it) }
    val accent = if (inTune) scheme.inTune else scheme.target
    val octaveLabel = midi?.let { "Octave ${NoteMath.octave(it)}" } ?: "Listening"
    val description = midi?.let { "Nearest note ${NoteMath.name(it, notation)}" } ?: "No note detected"

    Column(
        modifier = modifier.semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            repeat(PITCH_CLASSES) { pitchClass ->
                // Any octave spells the pitch class the same way; C4 keeps the names short and unambiguous.
                val label = NoteMath.letter(NoteMath.A4_MIDI - 9 + pitchClass, notation)
                NoteCell(label = label, lit = pitchClass == current, accent = accent)
            }
        }
        Text(
            text = octaveLabel,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun NoteCell(label: String, lit: Boolean, accent: androidx.compose.ui.graphics.Color) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(
        targetValue = if (lit) accent else scheme.outlineVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cellBorder",
    )
    val text by animateColorAsState(
        targetValue = if (lit) accent else scheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cellText",
    )
    // The lit cell squares off, the way a targeted string button does, so the two modes read as one app.
    val corner by animateFloatAsState(
        targetValue = if (lit) 30f else 50f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cellCorner",
    )
    val shape = RoundedCornerShape(percent = corner.toInt())

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(shape)
            .background(if (lit) scheme.surfaceContainerHigh else scheme.surfaceContainerLow)
            .border(if (lit) 2.dp else 1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = text,
            maxLines = 1,
            softWrap = false,
        )
    }
}
