package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntainy.guitar_tuner.ui.theme.Bricolage
import dev.ntainy.guitar_tuner.ui.theme.inTune

val NOTE_GLYPH_SIZE = 96.dp

/**
 * The target note in Bricolage bold inside a 96 dp ring, with the octave raised and smaller; a mint glow behind it
 * while in tune. [pitchLabel] ("110.19 Hz") is shown underneath when present; the slot is kept so nothing jumps.
 */
@Composable
fun NoteGlyph(
    letter: String?,
    octave: String?,
    ringColor: Color,
    inTune: Boolean,
    pitchLabel: String?,
    modifier: Modifier = Modifier,
    showPitchSlot: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val glow by animateFloatAsState(if (inTune) 1f else 0f, animationSpec = tween(250), label = "glow")
    val glowColor = scheme.inTune
    val description = if (letter == null) "No target note" else "Target note $letter$octave"

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(NOTE_GLYPH_SIZE)
                .semantics { contentDescription = description }
                .drawBehind {
                    if (glow > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowColor.copy(alpha = 0.45f * glow), glowColor.copy(alpha = 0f)),
                                center = center,
                                radius = size.minDimension * 0.85f,
                            ),
                            radius = size.minDimension * 0.85f,
                            center = center,
                        )
                    }
                }
                .background(scheme.surfaceContainer, CircleShape)
                .border(2.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val text = buildAnnotatedString {
                append(letter ?: "–")
                if (octave != null) {
                    withStyle(
                        SpanStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            baselineShift = BaselineShift(0.7f),
                        ),
                    ) {
                        append(octave)
                    }
                }
            }
            val glyphSize = if ((letter?.length ?: 1) > 1) 54.sp else 64.sp
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.Bold,
                    fontSize = glyphSize,
                    lineHeight = 64.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                color = if (letter == null) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (showPitchSlot) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = pitchLabel ?: " ",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
