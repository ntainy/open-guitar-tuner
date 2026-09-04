package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.ntainy.guitar_tuner.ui.theme.farOff
import dev.ntainy.guitar_tuner.ui.theme.inTune
import kotlin.math.abs

/** Half-width of the ruler in cents. */
const val GAUGE_RANGE_CENTS = 50.0

/** Beyond this the reading turns coral. */
const val FAR_OFF_CENTS = 25.0

/**
 * How much history the needle trail keeps. Long enough to see a pluck settle or a bend move, short enough that the
 * ghost needles never read as a second reading.
 */
const val TRACE_WINDOW_MS = 1_500L

/** Height of the ruler, labels included. */
val RULER_HEIGHT = 72.dp

private val RULER_INSET: Dp = 28.dp

/** The trail is sampled every 40 ms; every third sample is plenty for a tail this short. */
private const val TRAIL_STRIDE = 3
private const val TRAIL_ALPHA_NEWEST = 0.38f
private const val TRAIL_ALPHA_OLDEST = 0.08f

/**
 * One reading of the trail. [cents] is null for a frame with no stable pitch, which leaves a gap rather than a
 * ghost needle standing in silence.
 */
@Immutable
data class TracePoint(val atMs: Long, val cents: Float?)

/**
 * The colour of a reading — the cents figure, its hint and the needle: dimmed with no pitch, mint inside tolerance,
 * ink up to ±[FAR_OFF_CENTS], coral beyond. Brass is reserved for the target and never means "off".
 */
fun readingColor(centsOff: Double?, inTune: Boolean, scheme: ColorScheme): Color = when {
    centsOff == null -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
    inTune -> scheme.inTune
    abs(centsOff) <= FAR_OFF_CENTS -> scheme.onSurface
    else -> scheme.farOff
}

/**
 * Horizontal ruler from −50 to +50 cents with a spring-loaded needle. With no pitch the needle rests at 0, dimmed.
 * [trail] is the last [TRACE_WINDOW_MS] of readings, drawn as fading ghost needles behind the live one; pass an
 * empty list to draw none.
 */
@Composable
fun CentsGauge(
    centsOff: Double?,
    inTune: Boolean,
    toleranceCents: Double,
    hint: String,
    modifier: Modifier = Modifier,
    centsLabel: String? = centsOff?.let(::formatCents),
    trail: List<TracePoint> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    val clamped = (centsOff ?: 0.0).coerceIn(-GAUGE_RANGE_CENTS, GAUGE_RANGE_CENTS).toFloat()
    val needle by animateFloatAsState(
        targetValue = clamped,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "needle",
    )
    val needleColor by animateColorAsState(
        targetValue = readingColor(centsOff, inTune, scheme),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "needleColor",
    )
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val glyphStyle = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurfaceVariant)
    val description = if (centsLabel == null) hint else "$centsLabel cents, $hint"

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        drawRuler(
            needleCents = needle,
            needleColor = needleColor,
            inTune = inTune,
            hasPitch = centsOff != null,
            toleranceCents = toleranceCents.toFloat(),
            trail = trail,
            scheme = scheme,
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            glyphStyle = glyphStyle,
        )
    }
}

private fun DrawScope.drawRuler(
    needleCents: Float,
    needleColor: Color,
    inTune: Boolean,
    hasPitch: Boolean,
    toleranceCents: Float,
    trail: List<TracePoint>,
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

    val needleTop = 0f
    val needleBottom = baseline + 4.dp.toPx()
    val needleWidth = 3.dp.toPx()

    // The trail: where the needle has been over the last window, fading with age, newest on top.
    if (trail.isNotEmpty()) {
        val nowMs = trail.last().atMs
        for (i in trail.indices.reversed() step TRAIL_STRIDE) {
            val point = trail[i]
            val cents = point.cents ?: continue
            val age = ((nowMs - point.atMs).toFloat() / TRACE_WINDOW_MS).coerceIn(0f, 1f)
            val x = xAt(cents.coerceIn(-range, range))
            drawLine(
                color = needleColor.copy(alpha = lerp(TRAIL_ALPHA_NEWEST, TRAIL_ALPHA_OLDEST, age)),
                start = Offset(x, needleTop),
                end = Offset(x, needleBottom),
                strokeWidth = needleWidth,
                cap = StrokeCap.Round,
            )
        }
    }

    // Needle: a soft glow when in tune, then the rounded 3 dp needle itself.
    val nx = xAt(needleCents)
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
        strokeWidth = needleWidth,
        cap = StrokeCap.Round,
    )
}
