package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.ui.theme.Bricolage
import dev.ntainy.guitar_tuner.ui.theme.inTune

/** The ring at its ordinary size; the letter and octave scale with it. */
val NOTE_GLYPH_SIZE = 96.dp

/** The ring as the centrepiece of chromatic mode, where it stands in for the headstock. */
val CHROMATIC_GLYPH_SIZE = 144.dp

/**
 * The target note in Bricolage bold inside a ring, with the octave raised and smaller; a mint glow behind it while
 * in tune. The type is sized from [size] rather than from the font scale, so the letter always fits its ring.
 */
@Composable
fun NoteGlyph(
    letter: String?,
    octave: String?,
    ringColor: Color,
    inTune: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = NOTE_GLYPH_SIZE,
) {
    val scheme = MaterialTheme.colorScheme
    val glow by animateFloatAsState(
        targetValue = if (inTune) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "glow",
    )
    val glowColor = scheme.inTune
    val description = if (letter == null) "No target note" else "Target note $letter$octave"
    val density = LocalDensity.current
    // 96 dp of ring carries a 64 sp letter and a 22 sp octave; a two-character letter ("F♯") drops a size.
    val letterSize = with(density) { (size * if ((letter?.length ?: 1) > 1) 0.56f else 0.667f).toSp() }
    val octaveSize = with(density) { (size * 0.23f).toSp() }
    val lineHeight = with(density) { (size * 0.667f).toSp() }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .drawBehind {
                if (glow > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.45f * glow), glowColor.copy(alpha = 0f)),
                            center = center,
                            radius = this.size.minDimension * 0.85f,
                        ),
                        radius = this.size.minDimension * 0.85f,
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
                        fontSize = octaveSize,
                        fontWeight = FontWeight.SemiBold,
                        baselineShift = BaselineShift(0.7f),
                    ),
                ) {
                    append(octave)
                }
            }
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = Bricolage,
                fontWeight = FontWeight.Bold,
                fontSize = letterSize,
                lineHeight = lineHeight,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            color = if (letter == null) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}
