package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.STRING_COUNT
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadstockSpecTest {
    @Test
    fun everyLayoutHasOneAnchorPerStringOnTheArt() {
        HeadstockLayout.entries.forEach { layout ->
            val spec = layout.spec
            assertEquals(STRING_COUNT, spec.pegAnchors.size, layout.name)
            spec.pegAnchors.forEach { anchor ->
                val onTheArt = anchor.x in 0.05f..0.95f && anchor.y in 0.05f..0.95f
                assertTrue(onTheArt, "$layout anchor $anchor is off the art")
            }
        }
    }

    @Test
    fun theCropKeepsMostOfTheImageAndEveryPostStaysInsideTheVisiblePart() {
        HeadstockLayout.entries.forEach { layout ->
            val spec = layout.spec
            val visible = spec.visible
            val insideTheImage = visible.left >= 0f && visible.top >= 0f && visible.right <= 1f && visible.bottom <= 1f
            assertTrue(insideTheImage, layout.name)
            assertTrue(visible.width > 0.5f && visible.height > 0.5f, "$layout trims more than half the art away")
            assertTrue(spec.visibleHeightFraction > 0.5f && spec.visibleHeightFraction <= 1f, layout.name)
            assertEquals(visible.height, spec.visibleHeightFraction, layout.name)
            assertEquals(STRING_COUNT, spec.visibleAnchors.size, layout.name)
            spec.visibleAnchors.forEach { anchor ->
                assertTrue(anchor.x > 0f && anchor.x < 1f, "$layout visible anchor $anchor is off the art")
                assertTrue(anchor.y > 0f && anchor.y < 1f, "$layout visible anchor $anchor is under the crop")
            }
            // Room for a button around every post: a button centred on a post must not hang off an edge.
            val clearOfTheEdges = spec.visibleAnchors.all { it.y > 0.1f && it.x > 0.1f && it.x < 0.9f }
            assertTrue(clearOfTheEdges, "$layout posts too near an edge")
        }
    }

    @Test
    fun anchorsMapThroughTheCropByDividingYWhenOnlyTheBottomIsTrimmed() {
        val spec = HeadstockSpecs.THREE_PLUS_THREE
        assertEquals(0f, spec.visible.left)
        assertEquals(0f, spec.visible.top)
        spec.pegAnchors.zip(spec.visibleAnchors).forEach { (image, visible) ->
            assertEquals(image.x, visible.x)
            assertEquals(image.y / spec.visibleHeightFraction, visible.y, 1e-6f)
        }
        // The visible part is the image's width over the cropped height.
        assertEquals(411f / (770f * spec.visibleHeightFraction), spec.visibleAspect, 1e-5f)
    }

    @Test
    fun anchorsMapThroughATrimmedMarginByOffsetAndScale() {
        val spec = HeadstockSpecs.SIX_IN_LINE
        val visible = spec.visible
        spec.pegAnchors.zip(spec.visibleAnchors).forEach { (image, mapped) ->
            assertEquals((image.x - visible.left) / visible.width, mapped.x, 1e-6f)
            assertEquals((image.y - visible.top) / visible.height, mapped.y, 1e-6f)
        }
        assertEquals((visible.width * 607f) / (visible.height * 882f), spec.visibleAspect, 1e-5f)
    }

    @Test
    fun threePlusThreeHasTwoColumnsWithLowEBottomLeftAndHighEBottomRight() {
        val spec = HeadstockSpecs.THREE_PLUS_THREE
        val left = spec.pegAnchors.take(3)
        val right = spec.pegAnchors.drop(3)
        assertTrue(left.all { it.x == left[0].x } && left[0].x < 0.5f, "left column shares one x on the left half")
        assertTrue(right.all { it.x == right[0].x } && right[0].x > 0.5f, "right column shares one x on the right half")
        assertTrue(left[0].y > left[1].y && left[1].y > left[2].y, "low E, A, D go bottom to top")
        assertTrue(right[0].y < right[1].y && right[1].y < right[2].y, "G, B, high E go top to bottom")
        assertEquals(left.map { it.y }, right.map { it.y }.reversed(), "rows line up across the headstock")
    }

    @Test
    fun sixInLineRunsDiagonallyFromLowEAtTheBottomToHighEAtTheTop() {
        val spec = HeadstockSpecs.SIX_IN_LINE
        spec.pegAnchors.zipWithNext { a, b ->
            assertTrue(b.x > a.x, "posts move right going up: $a -> $b")
            assertTrue(a.y > b.y, "posts move up with string index: $a -> $b")
            assertEquals(0.078f, a.y - b.y, 0.006f)
        }
    }

    @Test
    fun minAnchorSpacingIsTheClosestPairOnceDrawn() {
        val spec = HeadstockSpecs.THREE_PLUS_THREE
        val width = 400f
        val height = width / spec.visibleAspect
        // Rows are ≈0.167 of the image apart (the lower pair a hair closer); the columns are further apart.
        val rowGap = (0.490f - 0.324f) / spec.visible.height * height
        assertEquals(rowGap, spec.minAnchorSpacing(width, height), 0.5f)

        // On the diagonal the neighbours are the closest pairs; whichever of them is tightest sets the spacing.
        val diagonal = HeadstockSpecs.SIX_IN_LINE
        val h = 800f
        val w = h * diagonal.visibleAspect
        val tightest = diagonal.pegAnchors.zipWithNext { a, b ->
            val dx = (b.x - a.x) / diagonal.visible.width * w
            val dy = (a.y - b.y) / diagonal.visible.height * h
            sqrt(dx * dx + dy * dy)
        }.min()
        assertEquals(tightest, diagonal.minAnchorSpacing(w, h), 0.5f)
    }

    @Test
    fun buttonsAreFullSizeWithRoomAndShrinkToKeepAGapButNeverBelowTheFloor() {
        assertEquals(STRING_BUTTON_SIZE, stringButtonSize(200.dp), "plenty of room")
        assertEquals(STRING_BUTTON_SIZE, stringButtonSize(STRING_BUTTON_SIZE + STRING_BUTTON_MIN_GAP), "just enough")
        assertEquals(50.dp, stringButtonSize(54.dp), "the gap wins over the size")
        assertEquals(MIN_STRING_BUTTON_SIZE, stringButtonSize(30.dp), "the floor wins over the gap")
        assertEquals(60.dp, STRING_BUTTON_SIZE)
        assertEquals(40.dp, MIN_STRING_BUTTON_SIZE)
    }
}
