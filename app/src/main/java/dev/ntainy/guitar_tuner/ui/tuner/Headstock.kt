package dev.ntainy.guitar_tuner.ui.tuner

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.R
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.ui.theme.inTune
import dev.ntainy.guitar_tuner.ui.theme.target
import kotlin.math.roundToInt

/** Which side of the headstock art a string's button sits on. */
enum class ButtonSide { LEFT, RIGHT }

/**
 * Where the tuning posts are in a headstock drawable.
 *
 * @property pegAnchors centre of each post as fractions of the image size, index 0 = lowest string (low E in Standard)
 * @property ringRadiusFraction radius of the highlight ring as a fraction of the drawn image width
 * @property buttonSides which column each string's button goes in, index 0 = lowest string
 */
@Immutable
data class HeadstockSpec(
    @param:DrawableRes val drawable: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val pegAnchors: List<Offset>,
    val ringRadiusFraction: Float,
    val buttonSides: List<ButtonSide>,
) {
    val aspect: Float get() = imageWidth.toFloat() / imageHeight

    val columns: Int get() = buttonSides.distinct().size
}

object HeadstockSpecs {
    /**
     * 3+3 (411×770). Anchors measured by overlaying rings on the PNG and refining with the centroid of the silver
     * pixels of each post cap (see the Wave 2 D notes): left column x = 0.28, right column x = 0.73, rows at
     * y = 0.157 / 0.324 / 0.490. Low E is the bottom-left post nearest the nut, high E the bottom-right.
     */
    val THREE_PLUS_THREE = HeadstockSpec(
        drawable = R.drawable.headstock_3_3,
        imageWidth = 411,
        imageHeight = 770,
        pegAnchors = listOf(
            Offset(0.280f, 0.490f), // 0 low E  (bottom left)
            Offset(0.280f, 0.324f), // 1 A
            Offset(0.280f, 0.157f), // 2 D      (top left)
            Offset(0.730f, 0.157f), // 3 G      (top right)
            Offset(0.730f, 0.324f), // 4 B
            Offset(0.730f, 0.490f), // 5 high E (bottom right)
        ),
        ringRadiusFraction = 0.052f,
        buttonSides = listOf(
            ButtonSide.LEFT, ButtonSide.LEFT, ButtonSide.LEFT,
            ButtonSide.RIGHT, ButtonSide.RIGHT, ButtonSide.RIGHT,
        ),
    )

    /**
     * 6-in-line (607×882). Posts run diagonally from low E at the bottom (0.341, 0.584) to high E at the top
     * (0.526, 0.195), one every ≈0.078 of the height; centroid-refined per post. All buttons sit in one column on the
     * left, high E on top.
     */
    val SIX_IN_LINE = HeadstockSpec(
        drawable = R.drawable.headstock_6_in_line,
        imageWidth = 607,
        imageHeight = 882,
        pegAnchors = listOf(
            Offset(0.341f, 0.584f), // 0 low E  (bottom)
            Offset(0.381f, 0.508f), // 1 A
            Offset(0.418f, 0.429f), // 2 D
            Offset(0.454f, 0.353f), // 3 G
            Offset(0.489f, 0.271f), // 4 B
            Offset(0.526f, 0.195f), // 5 high E (top)
        ),
        ringRadiusFraction = 0.044f,
        buttonSides = List(6) { ButtonSide.LEFT },
    )
}

val HeadstockLayout.spec: HeadstockSpec
    get() = when (this) {
        HeadstockLayout.THREE_PLUS_THREE -> HeadstockSpecs.THREE_PLUS_THREE
        HeadstockLayout.SIX_IN_LINE -> HeadstockSpecs.SIX_IN_LINE
    }

private const val IMAGE_ID = "headstock"
private val COLUMN_GAP = 12.dp
private val BUTTON_MIN_GAP = 8.dp

/**
 * Headstock art with the post rings plus the six string buttons in columns beside it, each button on the row of its
 * post (spread apart only when the posts are closer together than a button). The image takes all the height it can;
 * buttons are placed with plain layout maths, so everything stays aligned at any size.
 */
@Composable
fun Headstock(
    layout: HeadstockLayout,
    strings: List<StringUi>,
    onStringTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    manualMode: Boolean = false,
) {
    val spec = layout.spec
    val targetIndex = strings.firstOrNull { it.isTarget }?.index
    val tuned = remember(strings) { strings.filter { it.isTuned }.map { it.index }.toSet() }

    Layout(
        modifier = modifier,
        content = {
            HeadstockImage(
                spec = spec,
                targetIndex = targetIndex,
                tunedStrings = tuned,
                modifier = Modifier.layoutId(IMAGE_ID),
            )
            strings.forEach { string ->
                StringButton(
                    label = string.label,
                    state = StringButtonState.of(isTarget = string.isTarget, isTuned = string.isTuned),
                    onClick = { onStringTap(string.index) },
                    modifier = Modifier.layoutId(string.index),
                    contentDescription = string.accessibilityLabel,
                    dimmed = manualMode && !string.isTarget,
                )
            }
        },
    ) { measurables, constraints ->
        val gapPx = COLUMN_GAP.roundToPx()
        val minGapPx = BUTTON_MIN_GAP.roundToPx()
        // Six buttons in one column need 6 × 56 dp; on a short screen shrink them (down to 30 dp) so they fit
        // with the gap intact: the gap wins over the size.
        val perSide = ButtonSide.entries.maxOf { side -> strings.count { spec.buttonSides.getOrNull(it.index) == side } }
            .coerceAtLeast(1)
        val fullPx = STRING_BUTTON_SIZE.roundToPx()
        val buttonPx = if (constraints.hasBoundedHeight) {
            minOf(fullPx, (constraints.maxHeight - (perSide - 1) * minGapPx) / perSide)
                .coerceAtLeast(MIN_STRING_BUTTON_SIZE.roundToPx())
        } else {
            fullPx
        }
        val minSpacing = (buttonPx + minGapPx).toFloat()
        val columns = spec.columns
        val width = constraints.maxWidth
        val imageMaxW = (width - columns * (buttonPx + gapPx)).coerceAtLeast(1)
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else (imageMaxW / spec.aspect).roundToInt()
        val imageH = minOf(height.toFloat(), imageMaxW / spec.aspect)
        val imageW = imageH * spec.aspect
        val image = measurables.first { it.layoutId == IMAGE_ID }
            .measure(Constraints.fixed(imageW.roundToInt().coerceAtLeast(0), imageH.roundToInt().coerceAtLeast(0)))
        val contentW = image.width + columns * (buttonPx + gapPx)
        val left = ((width - contentW) / 2).coerceAtLeast(0)
        val hasLeftColumn = ButtonSide.LEFT in spec.buttonSides
        val imageX = left + if (hasLeftColumn) buttonPx + gapPx else 0
        val imageY = ((height - image.height) / 2).coerceAtLeast(0)
        val buttons = strings.associate { string ->
            val measurable = measurables.first { it.layoutId == string.index }
            string.index to measurable.measure(Constraints.fixed(buttonPx, buttonPx))
        }

        // Every button wants the row of its post; a column is spread only when the posts are too close for 56 dp.
        val lo = buttonPx / 2f
        val hi = (height - buttonPx / 2f).coerceAtLeast(lo)
        val centres = HashMap<Int, Float>(strings.size)
        ButtonSide.entries.forEach { side ->
            val members = strings.filter { spec.buttonSides.getOrNull(it.index) == side }
            if (members.isEmpty()) return@forEach
            val desired = members.map { imageY + (spec.pegAnchors.getOrNull(it.index)?.y ?: 0.5f) * image.height }
            val placed = spreadCentres(desired, minSpacing, lo, hi)
            members.forEachIndexed { k, string -> centres[string.index] = placed[k] }
        }

        layout(width, height) {
            image.placeRelative(imageX, imageY)
            strings.forEach { string ->
                val side = spec.buttonSides.getOrNull(string.index) ?: ButtonSide.LEFT
                val x = if (side == ButtonSide.LEFT) left else imageX + image.width + gapPx
                val y = ((centres[string.index] ?: lo) - buttonPx / 2f).roundToInt()
                buttons.getValue(string.index).placeRelative(x, y)
            }
        }
    }
}

/**
 * The drawable with a mint ring on every tuned post and an animated brass ring on the target post. Rings are mapped
 * through the same fit maths as [ContentScale.Fit], so they stay on the posts whatever the composable's size.
 */
@Composable
fun HeadstockImage(
    spec: HeadstockSpec,
    targetIndex: Int?,
    tunedStrings: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val brass = scheme.target
    val mint = scheme.inTune
    val ring = remember(spec) { Animatable(spec.pegAnchors[targetIndex ?: 0], Offset.VectorConverter) }
    LaunchedEffect(spec, targetIndex) {
        if (targetIndex != null) {
            val anchor = spec.pegAnchors.getOrNull(targetIndex)
            if (anchor != null) ring.animateTo(anchor, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    val ringAlpha by animateFloatAsState(if (targetIndex != null) 1f else 0f, label = "ringAlpha")
    val targetTuned = targetIndex != null && targetIndex in tunedStrings

    Image(
        painter = painterResource(spec.drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.drawWithContent {
            drawContent()
            val scale = minOf(size.width / spec.imageWidth, size.height / spec.imageHeight)
            val drawnW = spec.imageWidth * scale
            val drawnH = spec.imageHeight * scale
            val origin = Offset((size.width - drawnW) / 2f, (size.height - drawnH) / 2f)
            fun at(fraction: Offset) = Offset(origin.x + fraction.x * drawnW, origin.y + fraction.y * drawnH)
            val radius = spec.ringRadiusFraction * drawnW
            val stroke = 2.5.dp.toPx()
            tunedStrings.forEach { index ->
                spec.pegAnchors.getOrNull(index)?.let { drawCircle(mint, radius, at(it), style = Stroke(stroke)) }
            }
            if (ringAlpha > 0f) {
                val centre = at(ring.value)
                val r = if (targetTuned) radius + stroke * 1.8f else radius
                drawCircle(brass.copy(alpha = 0.22f * ringAlpha), r + stroke * 1.5f, centre)
                drawCircle(brass.copy(alpha = ringAlpha), r, centre, style = Stroke(stroke))
            }
        },
    )
}

/**
 * Places button centres so consecutive ones are at least [minSpacing] apart and all lie within [lo]..[hi].
 * When the [desired] rows already have room they are used as is; otherwise the column is packed evenly around the
 * middle of the desired span. Result is in the input order.
 */
internal fun spreadCentres(desired: List<Float>, minSpacing: Float, lo: Float, hi: Float): List<Float> {
    if (desired.isEmpty()) return desired
    val order = desired.indices.sortedBy { desired[it] }
    val sorted = order.map { desired[it] }
    val n = sorted.size
    val roomy = (1 until n).all { sorted[it] - sorted[it - 1] >= minSpacing }
    val placed = if (roomy) {
        sorted.toFloatArray()
    } else {
        val start = (sorted.first() + sorted.last()) / 2f - (n - 1) * minSpacing / 2f
        FloatArray(n) { start + it * minSpacing }
    }
    val span = placed[n - 1] - placed[0]
    when {
        span > hi - lo -> {
            val step = if (n > 1) (hi - lo) / (n - 1) else 0f
            for (i in 0 until n) placed[i] = lo + i * step
        }
        placed[0] < lo -> {
            val shift = lo - placed[0]
            for (i in 0 until n) placed[i] += shift
        }
        placed[n - 1] > hi -> {
            val shift = placed[n - 1] - hi
            for (i in 0 until n) placed[i] -= shift
        }
    }
    val result = FloatArray(n)
    order.forEachIndexed { k, originalIndex -> result[originalIndex] = placed[k] }
    return result.toList()
}
