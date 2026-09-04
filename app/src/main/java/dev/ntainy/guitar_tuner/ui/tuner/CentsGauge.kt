package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.ui.theme.farOff
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.target
import kotlin.math.abs
import kotlin.math.roundToInt

/** Half-width of the ruler in cents. */
const val GAUGE_RANGE_CENTS = 50.0

/** Beyond this the needle turns coral. */
const val FAR_OFF_CENTS = 25.0

private const val BUBBLE_ID = "bubble"
private const val RULER_ID = "ruler"
private val RULER_HEIGHT = 72.dp
private val RULER_INSET: Dp = 28.dp
private val BUBBLE_GAP = 6.dp

/**
 * The colour of the needle, note ring and offset bubble for a reading: dimmed with no pitch, mint inside tolerance,
 * brass up to ±[FAR_OFF_CENTS], coral beyond.
 */
fun needleColor(centsOff: Double?, inTune: Boolean, scheme: ColorScheme): Color = when {
    centsOff == null -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
    inTune -> scheme.inTune
    abs(centsOff) <= FAR_OFF_CENTS -> scheme.target
    else -> scheme.farOff
}

/**
 * Horizontal ruler from −50 to +50 cents with a spring-loaded needle and an offset bubble riding above it.
 * With no pitch the needle rests at 0, dimmed, and the bubble reads "Play a string".
 */
@Composable
fun CentsGauge(
    centsOff: Double?,
    inTune: Boolean,
    toleranceCents: Double,
    hint: String,
    modifier: Modifier = Modifier,
    centsLabel: String? = centsOff?.let(::formatCents),
) {
    val scheme = MaterialTheme.colorScheme
    val clamped = (centsOff ?: 0.0).coerceIn(-GAUGE_RANGE_CENTS, GAUGE_RANGE_CENTS).toFloat()
    val needle by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "needle",
    )
    val needleColor by animateColorAsState(needleColor(centsOff, inTune, scheme), label = "needleColor")
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val glyphStyle = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurfaceVariant)
    val description = if (centsLabel == null) hint else "$centsLabel cents, $hint"

    Layout(
        modifier = modifier.semantics { contentDescription = description },
        content = {
            OffsetBubble(
                centsLabel = centsLabel,
                hint = hint,
                accent = needleColor,
                modifier = Modifier.layoutId(BUBBLE_ID),
            )
            Canvas(modifier = Modifier.layoutId(RULER_ID).fillMaxWidth().height(RULER_HEIGHT)) {
                drawRuler(
                    needleCents = needle,
                    needleColor = needleColor,
                    inTune = inTune,
                    hasPitch = centsOff != null,
                    toleranceCents = toleranceCents.toFloat(),
                    scheme = scheme,
                    textMeasurer = textMeasurer,
                    labelStyle = labelStyle,
                    glyphStyle = glyphStyle,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val ruler = measurables.first { it.layoutId == RULER_ID }
            .measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
        val bubble = measurables.first { it.layoutId == BUBBLE_ID }
            .measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = BUBBLE_GAP.roundToPx()
        val height = bubble.height + gap + ruler.height
        layout(width, height) {
            ruler.placeRelative(0, bubble.height + gap)
            val inset = RULER_INSET.toPx()
            val range = GAUGE_RANGE_CENTS.toFloat()
            val needleX = inset + (needle + range) / (2f * range) * (width - 2 * inset)
            val x = (needleX - bubble.width / 2f).roundToInt().coerceIn(0, (width - bubble.width).coerceAtLeast(0))
            bubble.placeRelative(x, 0)
        }
    }
}

/** Rounded pill above the needle: "+3 · Tune down", or just the hint when there is no reading. */
@Composable
private fun OffsetBubble(centsLabel: String?, hint: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.5.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            // Same height with or without the cents figure, so the gauge below never shifts.
            .defaultMinSize(minHeight = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (centsLabel != null) {
            Text(text = centsLabel, style = MaterialTheme.typography.titleMedium, color = accent)
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawRuler(
    needleCents: Float,
    needleColor: Color,
    inTune: Boolean,
    hasPitch: Boolean,
    toleranceCents: Float,
    scheme: ColorScheme,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    glyphStyle: TextStyle,
) {
    val range = GAUGE_RANGE_CENTS.toFloat()
    val inset = RULER_INSET.toPx()
    val x0 = inset
    val span = size.width - 2 * inset
    fun xAt(cents: Float): Float = x0 + (cents + range) / (2f * range) * span
    val baseline = size.height - 22.dp.toPx()
    val majorTick = 18.dp.toPx()
    val minorTick = 10.dp.toPx()

    // Mint band spanning ±tolerance around zero.
    val bandHalf = (toleranceCents / (2f * range) * span).coerceAtLeast(2.dp.toPx())
    drawRoundRect(
        color = scheme.inTune.copy(alpha = if (inTune) 0.30f else 0.18f),
        topLeft = Offset(xAt(0f) - bandHalf, 0f),
        size = Size(bandHalf * 2, baseline),
        cornerRadius = CornerRadius(3.dp.toPx()),
    )

    // Baseline and ticks.
    drawLine(scheme.outline, Offset(x0, baseline), Offset(x0 + span, baseline), strokeWidth = 1.5.dp.toPx())
    for (c in -50..50 step 10) {
        val major = c == 0 || abs(c) == 50
        val x = xAt(c.toFloat())
        drawLine(
            color = if (c == 0) scheme.onSurface else scheme.onSurfaceVariant,
            start = Offset(x, baseline - if (major) majorTick else minorTick),
            end = Offset(x, baseline),
            strokeWidth = (if (major) 2.dp else 1.5.dp).toPx(),
            cap = StrokeCap.Round,
        )
    }

    // Labels under the major ticks, accidentals in the insets.
    val labelY = baseline + 5.dp.toPx()
    listOf(-50 to "−50", 0 to "0", 50 to "+50").forEach { (c, text) ->
        val layout = textMeasurer.measure(text, labelStyle)
        drawText(layout, topLeft = Offset(xAt(c.toFloat()) - layout.size.width / 2f, labelY))
    }
    val glyphCentreY = baseline - majorTick / 2f
    val flat = textMeasurer.measure("♭", glyphStyle)
    drawText(flat, topLeft = Offset(x0 / 2f - flat.size.width / 2f, glyphCentreY - flat.size.height / 2f))
    val sharp = textMeasurer.measure("♯", glyphStyle)
    drawText(
        sharp,
        topLeft = Offset(size.width - x0 / 2f - sharp.size.width / 2f, glyphCentreY - sharp.size.height / 2f),
    )

    // Needle: a soft glow when in tune, then the rounded 3 dp needle itself.
    val nx = xAt(needleCents)
    val needleTop = 0f
    val needleBottom = baseline + 4.dp.toPx()
    if (inTune && hasPitch) {
        drawLine(
            color = needleColor.copy(alpha = 0.28f),
            start = Offset(nx, needleTop),
            end = Offset(nx, needleBottom),
            strokeWidth = 10.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawLine(
        color = needleColor,
        start = Offset(nx, needleTop),
        end = Offset(nx, needleBottom),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
