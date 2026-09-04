package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntainy.guitar_tuner.ui.theme.Bricolage

/** Height of the figure row. Fixed, so the ruler under it never moves when a reading appears or dies away. */
val READOUT_ROW_HEIGHT = 62.dp

/**
 * Room for four glyphs ("−107") in the figure's face: the ruler clamps at ±50 but the figure tells the truth, and a
 * string a semitone out reads three digits. The figure is right-aligned in it so the hint beside it never jumps.
 */
private val FIGURE_WIDTH = 140.dp

/**
 * The reading: the cents figure with its hint and the target note beside it, over the ruler. Every part of it
 * takes the same colour from [readingColor], so the figure, the hint and the needle always agree. With no pitch
 * the row shows the idle [hint] alone at the same height.
 */
@Composable
fun Readout(
    centsOff: Double?,
    centsLabel: String?,
    inTune: Boolean,
    hint: String,
    detail: String?,
    toleranceCents: Double,
    modifier: Modifier = Modifier,
    trail: List<TracePoint> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = readingColor(centsOff, inTune, scheme),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reading",
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ReadingRow(
            centsLabel = centsLabel,
            hint = hint,
            detail = detail,
            color = color,
            modifier = Modifier.fillMaxWidth().height(READOUT_ROW_HEIGHT),
        )
        Spacer(Modifier.height(8.dp))
        CentsGauge(
            centsOff = centsOff,
            inTune = inTune,
            toleranceCents = toleranceCents,
            hint = hint,
            centsLabel = centsLabel,
            trail = trail,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadingRow(
    centsLabel: String?,
    hint: String,
    detail: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (centsLabel == null) {
            Text(
                text = hint,
                style = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            Box(modifier = Modifier.width(FIGURE_WIDTH), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = centsLabel,
                    style = TextStyle(
                        fontFamily = Bricolage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 56.sp,
                        lineHeight = 56.sp,
                        fontFeatureSettings = "tnum",
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = hint,
                    style = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = color,
                    maxLines = 1,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
