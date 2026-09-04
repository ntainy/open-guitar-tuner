package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.ui.theme.inTune

/** How much history the trace keeps and draws. Long enough to watch a pluck settle, short enough to stay readable. */
const val TRACE_WINDOW_MS = 6_000L

/** Height of the strip. Deliberately short: it sits under the gauge and must not steal headstock room. */
val TRACE_HEIGHT = 56.dp

/** Corner radius of the panel. Matches the app's small shape so it sits in the same family as the other cards. */
private val TRACE_CORNER = 12.dp

/** Breathing room at the top and bottom so a reading clamped to ±50 cents still draws as a whole line. */
private val TRACE_EDGE_INSET = 5.dp

/**
 * One reading. [cents] is null for a frame with no stable pitch, which breaks the line rather than drawing a
 * straight jump through silence.
 */
@Immutable
data class TracePoint(val atMs: Long, val cents: Float?)

/**
 * The last [TRACE_WINDOW_MS] of cents readings, scrolling right to left.
 *
 * It answers a question the needle cannot: whether a string is settling, drifting or being bent. The in-tune band
 * is the same mint window the gauge draws, so the two read as one instrument.
 */
@Composable
fun PitchTrace(
    points: List<TracePoint>,
    toleranceCents: Double,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val range = GAUGE_RANGE_CENTS.toFloat()
    val description = "Pitch over the last ${TRACE_WINDOW_MS / 1000} seconds"

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACE_HEIGHT)
            // Without a ground of its own the strip reads as a stray line floating under the gauge rather than
            // as a panel with six seconds of history in it.
            .clip(RoundedCornerShape(TRACE_CORNER))
            .background(scheme.surfaceContainerLow)
            .semantics { contentDescription = description },
    ) {
        val h = size.height
        val w = size.width
        val mid = h / 2f
        // Keep the extremes off the panel edge: a reading pinned at ±50 should be a visible line, not a half-line
        // clipped by the rounded corner.
        val reach = mid - TRACE_EDGE_INSET.toPx()
        fun yAt(cents: Float): Float = mid - cents.coerceIn(-range, range) / range * reach
        fun xAt(atMs: Long): Float = w - (nowMs - atMs).toFloat() / TRACE_WINDOW_MS * w

        // Faint rules at ±25 cents, so the vertical scale is readable without a labelled axis.
        listOf(-0.5f, 0.5f).forEach { fraction ->
            val y = mid - fraction * reach
            drawLine(
                color = scheme.outlineVariant.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Mint band: the same ±tolerance window the gauge shades, laid on its side.
        val bandHalf = (toleranceCents.toFloat() / range * reach).coerceAtLeast(3.dp.toPx())
        drawRect(
            color = scheme.inTune.copy(alpha = 0.20f),
            topLeft = Offset(0f, mid - bandHalf),
            size = Size(w, bandHalf * 2),
        )
        drawLine(
            color = scheme.outlineVariant,
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )

        if (points.isEmpty()) return@Canvas

        // One sub-path per unbroken run of readings, so silence leaves a gap instead of a false slope.
        val path = Path()
        var drawing = false
        points.forEach { point ->
            val cents = point.cents
            if (cents == null) {
                drawing = false
                return@forEach
            }
            val x = xAt(point.atMs)
            val y = yAt(cents)
            if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
            drawing = true
        }
        drawPath(
            path = path,
            color = scheme.onSurfaceVariant,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // The newest reading, in the needle's colour, so the head of the line agrees with the gauge.
        val newest = points.lastOrNull { it.cents != null } ?: return@Canvas
        val cents = newest.cents ?: return@Canvas
        drawCircle(
            color = needleColor(cents.toDouble(), inTune = kotlin.math.abs(cents) <= toleranceCents, scheme = scheme),
            radius = 3.dp.toPx(),
            center = Offset(xAt(newest.atMs), yAt(cents)),
        )
    }
}
