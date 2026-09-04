package dev.ntainy.guitar_tuner.ui.tuner

import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.STRING_COUNT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadstockSpecTest {
    @Test
    fun everyLayoutHasOneAnchorAndOneButtonSidePerString() {
        HeadstockLayout.entries.forEach { layout ->
            val spec = layout.spec
            assertEquals(STRING_COUNT, spec.pegAnchors.size, layout.name)
            assertEquals(STRING_COUNT, spec.buttonSides.size, layout.name)
            spec.pegAnchors.forEach { anchor ->
                val onTheArt = anchor.x in 0.05f..0.95f && anchor.y in 0.05f..0.95f
                assertTrue(onTheArt, "$layout anchor $anchor is off the art")
            }
            assertTrue(spec.ringRadiusFraction in 0.02f..0.1f)
        }
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
        val expectedSides = listOf(ButtonSide.LEFT, ButtonSide.LEFT, ButtonSide.LEFT) +
            listOf(ButtonSide.RIGHT, ButtonSide.RIGHT, ButtonSide.RIGHT)
        assertEquals(expectedSides, spec.buttonSides)
        assertEquals(2, spec.columns)
    }

    @Test
    fun sixInLineRunsDiagonallyFromLowEAtTheBottomToHighEAtTheTop() {
        val spec = HeadstockSpecs.SIX_IN_LINE
        spec.pegAnchors.zipWithNext { a, b ->
            assertTrue(b.x > a.x, "posts move right going up: $a -> $b")
            assertTrue(a.y > b.y, "posts move up with string index: $a -> $b")
            assertEquals(0.078f, a.y - b.y, 0.006f)
        }
        assertTrue(spec.buttonSides.all { it == ButtonSide.LEFT })
        assertEquals(1, spec.columns)
    }

    @Test
    fun spreadKeepsRowsThatAlreadyHaveRoom() {
        val desired = listOf(300f, 100f, 200f)
        assertEquals(desired, spreadCentres(desired, minSpacing = 60f, lo = 0f, hi = 400f))
    }

    @Test
    fun spreadPacksTightRowsEvenlyAroundTheirMiddle() {
        val desired = listOf(120f, 100f, 140f)
        val placed = spreadCentres(desired, minSpacing = 62f, lo = 0f, hi = 400f)
        assertEquals(listOf(120f, 58f, 182f), placed)
        assertEquals(120f, placed.average().toFloat(), 1e-3f)
    }

    @Test
    fun spreadShiftsTheGroupBackInsideTheBounds() {
        val placed = spreadCentres(listOf(10f, 40f, 70f), minSpacing = 62f, lo = 28f, hi = 400f)
        assertEquals(listOf(28f, 90f, 152f), placed)

        val high = spreadCentres(listOf(390f, 395f), minSpacing = 62f, lo = 28f, hi = 400f)
        assertEquals(listOf(338f, 400f), high)
    }

    @Test
    fun spreadSqueezesOnlyWhenNothingElseFits() {
        val placed = spreadCentres(listOf(0f, 50f, 100f, 150f), minSpacing = 62f, lo = 28f, hi = 128f)
        assertEquals(listOf(28f, 61.333332f, 94.666664f, 128f), placed)
        assertTrue(placed.zipWithNext { a, b -> b > a }.all { it })
    }

    @Test
    fun spreadHandlesDegenerateInput() {
        assertEquals(emptyList(), spreadCentres(emptyList(), 60f, 0f, 100f))
        assertEquals(listOf(50f), spreadCentres(listOf(50f), 60f, 0f, 100f))
        assertEquals(listOf(100f), spreadCentres(listOf(150f), 60f, 0f, 100f))
    }
}
