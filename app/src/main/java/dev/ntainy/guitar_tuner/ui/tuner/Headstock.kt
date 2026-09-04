package dev.ntainy.guitar_tuner.ui.tuner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.R
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import kotlin.math.roundToInt

/**
 * Where the tuning posts are in a headstock drawable, and which part of it is shown.
 *
 * @property visible the part of the image that is drawn, as fractions of its size. The bottom edge sits just under
 *   the nut, so the neck and its strings never spend the screen's height on nothing; the other edges trim any
 *   transparent margin the PNG carries, so the art itself, not its canvas, is what fits the block
 * @property pegAnchors centre of each post as fractions of the whole image, index 0 = lowest string (low E in
 *   Standard); see [visibleAnchors] for the same points mapped through the crop
 */
@Immutable
data class HeadstockSpec(
    @param:DrawableRes val drawable: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val visible: Rect,
    val pegAnchors: List<Offset>,
) {
    /** How much of the image's height is drawn: the crop at the nut, less any transparent top. */
    val visibleHeightFraction: Float get() = visible.height

    /** Width over height of the part that is drawn. */
    val visibleAspect: Float get() = (visible.width * imageWidth) / (visible.height * imageHeight)

    /**
     * [pegAnchors] as fractions of the visible area. With nothing trimmed at the top or sides, an anchor at image
     * fraction y lands at y / [visibleHeightFraction].
     */
    val visibleAnchors: List<Offset> = pegAnchors.map {
        Offset((it.x - visible.left) / visible.width, (it.y - visible.top) / visible.height)
    }

    /** The smallest distance between any two posts once the visible area is drawn at [width] × [height]. */
    fun minAnchorSpacing(width: Float, height: Float): Float {
        val points = visibleAnchors.map { Offset(it.x * width, it.y * height) }
        var min = Float.POSITIVE_INFINITY
        for (i in points.indices) {
            for (j in i + 1 until points.size) min = minOf(min, (points[i] - points[j]).getDistance())
        }
        return min
    }
}

object HeadstockSpecs {
    /**
     * 3+3 (411×770). Anchors measured by overlaying rings on the PNG and refining with the centroid of the silver
     * pixels of each post cap (see the Wave 2 D notes): left column x = 0.28, right column x = 0.73, rows at
     * y = 0.157 / 0.324 / 0.490. Low E is the bottom-left post nearest the nut, high E the bottom-right.
     * The art fills the canvas edge to edge and the nut's bottom edge is at row 604 of 770.
     */
    val THREE_PLUS_THREE = HeadstockSpec(
        drawable = R.drawable.headstock_3_3,
        imageWidth = 411,
        imageHeight = 770,
        visible = Rect(left = 0f, top = 0f, right = 1f, bottom = 0.785f),
        pegAnchors = listOf(
            Offset(0.280f, 0.490f), // 0 low E  (bottom left)
            Offset(0.280f, 0.324f), // 1 A
            Offset(0.280f, 0.157f), // 2 D      (top left)
            Offset(0.730f, 0.157f), // 3 G      (top right)
            Offset(0.730f, 0.324f), // 4 B
            Offset(0.730f, 0.490f), // 5 high E (bottom right)
        ),
    )

    /**
     * 6-in-line (607×882). Posts run diagonally from low E at the bottom (0.341, 0.584) to high E at the top
     * (0.526, 0.195), one every ≈0.078 of the height; centroid-refined per post. The art sits in a canvas twice
     * its width (opaque columns 127..438, first opaque row 70), and the nut's bottom edge is at row 649 of 882.
     */
    val SIX_IN_LINE = HeadstockSpec(
        drawable = R.drawable.headstock_6_in_line,
        imageWidth = 607,
        imageHeight = 882,
        visible = Rect(left = 0.195f, top = 0.072f, right = 0.735f, bottom = 0.737f),
        pegAnchors = listOf(
            Offset(0.341f, 0.584f), // 0 low E  (bottom)
            Offset(0.381f, 0.508f), // 1 A
            Offset(0.418f, 0.429f), // 2 D
            Offset(0.454f, 0.353f), // 3 G
            Offset(0.489f, 0.271f), // 4 B
            Offset(0.526f, 0.195f), // 5 high E (top)
        ),
    )
}

val HeadstockLayout.spec: HeadstockSpec
    get() = when (this) {
        HeadstockLayout.THREE_PLUS_THREE -> HeadstockSpecs.THREE_PLUS_THREE
        HeadstockLayout.SIX_IN_LINE -> HeadstockSpecs.SIX_IN_LINE
    }

/** Room kept between neighbouring buttons when the posts sit closer together than a full-size button. */
val STRING_BUTTON_MIN_GAP = 4.dp

/** Height of the fade that softens the cut under the nut. */
private val HEADSTOCK_FADE = 40.dp

private const val IMAGE_ID = "headstock"

/**
 * The button size for posts [minAnchorSpacing] apart: full size while there is room, otherwise the gap wins and
 * the buttons shrink, down to [MIN_STRING_BUTTON_SIZE] and no further.
 */
fun stringButtonSize(minAnchorSpacing: Dp): Dp =
    minOf(STRING_BUTTON_SIZE, minAnchorSpacing - STRING_BUTTON_MIN_GAP).coerceAtLeast(MIN_STRING_BUTTON_SIZE)

/**
 * Headstock art cropped at the nut, with a string button centred on every tuning post. The art fits the block by
 * height or width, whichever binds, and is centred; the buttons are placed with plain layout maths through the
 * same crop, so they stay on the posts whatever the size.
 */
@Composable
fun Headstock(
    layout: HeadstockLayout,
    strings: List<StringUi>,
    onStringTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    manualMode: Boolean = false,
    onStringLongPress: ((Int) -> Unit)? = null,
) {
    val spec = layout.spec

    Layout(
        modifier = modifier,
        content = {
            HeadstockImage(spec = spec, modifier = Modifier.layoutId(IMAGE_ID))
            strings.forEach { string ->
                StringButton(
                    label = string.label,
                    state = StringButtonState.of(isTarget = string.isTarget, isTuned = string.isTuned),
                    onClick = { onStringTap(string.index) },
                    modifier = Modifier.layoutId(string.index),
                    contentDescription = string.accessibilityLabel,
                    dimmed = manualMode && !string.isTarget,
                    onLongClick = onStringLongPress?.let { { it(string.index) } },
                    onLongClickLabel = "Play ${string.label}",
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            (width / spec.visibleAspect).roundToInt()
        }
        val imageH = minOf(height.toFloat(), width / spec.visibleAspect)
        val imageW = imageH * spec.visibleAspect
        val image = measurables.first { it.layoutId == IMAGE_ID }
            .measure(Constraints.fixed(imageW.roundToInt().coerceAtLeast(0), imageH.roundToInt().coerceAtLeast(0)))
        val imageX = (width - image.width) / 2
        val imageY = (height - image.height) / 2

        val buttonPx = stringButtonSize(spec.minAnchorSpacing(imageW, imageH).toDp()).roundToPx()
        val buttons = strings.associate { string ->
            val measurable = measurables.first { it.layoutId == string.index }
            string.index to measurable.measure(Constraints.fixed(buttonPx, buttonPx))
        }

        layout(width, height) {
            image.placeRelative(imageX, imageY)
            strings.forEach { string ->
                val anchor = spec.visibleAnchors.getOrNull(string.index) ?: Offset(0.5f, 0.5f)
                val centreX = imageX + anchor.x * image.width
                val centreY = imageY + anchor.y * image.height
                buttons.getValue(string.index)
                    .placeRelative((centreX - buttonPx / 2f).roundToInt(), (centreY - buttonPx / 2f).roundToInt())
            }
        }
    }
}

/**
 * The [HeadstockSpec.visible] part of the drawable, scaled to fill the composable, with the bottom [HEADSTOCK_FADE]
 * dissolved into whatever is behind it so the cut under the nut reads as a fade, not an edge. Give it a size with
 * the visible aspect ratio.
 */
@Composable
fun HeadstockImage(spec: HeadstockSpec, modifier: Modifier = Modifier) {
    val bitmap = ImageBitmap.imageResource(spec.drawable)
    val srcOffset = IntOffset(
        (spec.visible.left * bitmap.width).roundToInt(),
        (spec.visible.top * bitmap.height).roundToInt(),
    )
    val srcSize = IntSize(
        (spec.visible.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - srcOffset.x),
        (spec.visible.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - srcOffset.y),
    )

    // The fade multiplies the art's own alpha, which needs the art in a layer of its own.
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawImage(
            image = bitmap,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            // The art is drawn larger than the PNG on most phones; bicubic keeps the strings from going jagged.
            filterQuality = FilterQuality.High,
        )
        val fade = HEADSTOCK_FADE.toPx().coerceAtMost(size.height)
        val top = size.height - fade
        // Eased rather than linear: the nut and strings are the brightest pixels in the art, and a straight ramp
        // leaves them as a faint hard line at the cut.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                0.25f to Color.Black.copy(alpha = 0.65f),
                0.5f to Color.Black.copy(alpha = 0.35f),
                0.75f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Transparent,
                startY = top,
                endY = size.height,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }
}
