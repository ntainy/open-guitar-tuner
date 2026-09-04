package dev.ntainy.guitar_tuner.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HysteresisTargetResolverTest {
    private val standard = intArrayOf(40, 45, 50, 55, 59, 64).map { NoteMath.frequency(it) }.toDoubleArray()
    private val doubleDaddy = intArrayOf(38, 45, 50, 50, 57, 62).map { NoteMath.frequency(it) }.toDoubleArray()

    private fun hzAtCents(midi: Int, cents: Double) = NoteMath.frequency(midi + cents / 100.0)

    @Test
    fun initialCallPicksTheNearestString() {
        val resolver = HysteresisTargetResolver()
        assertEquals(0, resolver.resolve(hzAtCents(40, 30.0), standard))
        resolver.reset()
        assertEquals(3, resolver.resolve(hzAtCents(55, -45.0), standard))
        resolver.reset()
        assertEquals(5, resolver.resolve(hzAtCents(64, 120.0), standard))
        resolver.reset()
        assertEquals(0, resolver.resolve(hzAtCents(40, -700.0), standard))
    }

    @Test
    fun noFlickerAtTheMidpointBetweenTwoStrings() {
        val resolver = HysteresisTargetResolver(switchMarginCents = 30.0, framesToSwitch = 3)
        assertEquals(0, resolver.resolve(hzAtCents(40, 0.0), standard))
        // 250 cents above E2 is exactly halfway to A2; wobble +-20 cents around it.
        val wobble = doubleArrayOf(250.0, 262.0, 240.0, 268.0, 255.0, 245.0, 266.0, 259.0, 270.0, 235.0)
        for (cents in wobble) {
            assertEquals(0, resolver.resolve(hzAtCents(40, cents), standard), "at +$cents cents")
        }
    }

    @Test
    fun switchesAfterFramesToSwitchConsecutiveFrames() {
        val resolver = HysteresisTargetResolver(switchMarginCents = 30.0, framesToSwitch = 3)
        assertEquals(0, resolver.resolve(hzAtCents(40, 0.0), standard))
        val a2 = hzAtCents(45, 0.0)
        assertEquals(0, resolver.resolve(a2, standard), "frame 1 keeps the current string")
        assertEquals(0, resolver.resolve(a2, standard), "frame 2 keeps the current string")
        assertEquals(1, resolver.resolve(a2, standard), "frame 3 switches")
        assertEquals(1, resolver.resolve(a2, standard))
    }

    @Test
    fun interruptedRunRestartsTheCount() {
        val resolver = HysteresisTargetResolver(switchMarginCents = 30.0, framesToSwitch = 3)
        assertEquals(0, resolver.resolve(hzAtCents(40, 0.0), standard))
        val a2 = hzAtCents(45, 0.0)
        resolver.resolve(a2, standard)
        resolver.resolve(a2, standard)
        assertEquals(0, resolver.resolve(hzAtCents(40, 5.0), standard), "back on E2 resets the candidate run")
        assertEquals(0, resolver.resolve(a2, standard))
        assertEquals(0, resolver.resolve(a2, standard))
        assertEquals(1, resolver.resolve(a2, standard))
    }

    @Test
    fun changingCandidateRestartsTheCount() {
        val resolver = HysteresisTargetResolver(switchMarginCents = 30.0, framesToSwitch = 3)
        assertEquals(0, resolver.resolve(hzAtCents(40, 0.0), standard))
        resolver.resolve(hzAtCents(45, 0.0), standard)
        resolver.resolve(hzAtCents(45, 0.0), standard)
        assertEquals(0, resolver.resolve(hzAtCents(50, 0.0), standard), "D3 is a new candidate: count restarts")
        assertEquals(0, resolver.resolve(hzAtCents(50, 0.0), standard))
        assertEquals(2, resolver.resolve(hzAtCents(50, 0.0), standard))
    }

    @Test
    fun marginMustBeExceededNotJustMet() {
        val resolver = HysteresisTargetResolver(switchMarginCents = 30.0, framesToSwitch = 1)
        assertEquals(0, resolver.resolve(hzAtCents(40, 0.0), standard))
        // +264 cents: 264 from E2, 236 from A2, difference 28 -> no switch.
        assertEquals(0, resolver.resolve(hzAtCents(40, 264.0), standard))
        // +266 cents: difference 32 -> switch on the first frame with framesToSwitch = 1.
        assertEquals(1, resolver.resolve(hzAtCents(40, 266.0), standard))
    }

    @Test
    fun duplicateTargetsResolveToTheLowestIndex() {
        val resolver = HysteresisTargetResolver()
        assertEquals(2, resolver.resolve(hzAtCents(50, 3.0), doubleDaddy))
        repeat(5) { assertEquals(2, resolver.resolve(hzAtCents(50, -3.0), doubleDaddy)) }
    }

    @Test
    fun currentDuplicateIsKeptWhenAlreadyOnTheHigherIndex() {
        val resolver = HysteresisTargetResolver(framesToSwitch = 1)
        // Land on index 3 first by being closest to it in a tuning where only index 3 is D3.
        val tuning = intArrayOf(38, 45, 47, 50, 57, 62).map { NoteMath.frequency(it) }.toDoubleArray()
        assertEquals(3, resolver.resolve(hzAtCents(50, 0.0), tuning))
        // Now the tuning turns into Double Daddy (index 2 is also D3): equal distance must not cause a switch.
        assertEquals(3, resolver.resolve(hzAtCents(50, 0.0), doubleDaddy))
    }

    @Test
    fun resetForgetsTheCurrentString() {
        val resolver = HysteresisTargetResolver()
        assertEquals(0, resolver.resolve(hzAtCents(40, 0.0), standard))
        assertEquals(0, resolver.resolve(hzAtCents(64, 0.0), standard), "held by hysteresis")
        resolver.reset()
        assertEquals(5, resolver.resolve(hzAtCents(64, 0.0), standard), "fresh start picks the nearest")
    }

    @Test
    fun rejectsBadInput() {
        val resolver = HysteresisTargetResolver()
        assertFailsWith<IllegalArgumentException> { resolver.resolve(110.0, DoubleArray(0)) }
        assertFailsWith<IllegalArgumentException> { resolver.resolve(0.0, standard) }
        assertFailsWith<IllegalArgumentException> { HysteresisTargetResolver(framesToSwitch = 0) }
    }
}

class HysteresisTargetResolverHarmonicsTest {
    private val standard = intArrayOf(40, 45, 50, 55, 59, 64).map { NoteMath.frequency(it) }.toDoubleArray()

    @Test
    fun octaveOfTheCurrentStringStaysOnItWithFoldedCents() {
        val r = HysteresisTargetResolver()
        repeat(3) { r.resolveMatch(196.2, standard) }
        repeat(6) {
            val m = r.resolveMatch(392.3, standard)!!
            kotlin.test.assertEquals(3, m.index)
            kotlin.test.assertEquals(2, m.fold)
            kotlin.test.assertEquals(1.3, m.centsOff, 0.3)
        }
    }

    @Test
    fun freshOctaveReadingResolvesToTheFundamentalString() {
        val r = HysteresisTargetResolver()
        val m = r.resolveMatch(165.76, standard)!!
        kotlin.test.assertEquals(0, m.index, "165.8 Hz is the low E an octave up, not a flat D3")
        kotlin.test.assertEquals(2, m.fold)
        kotlin.test.assertEquals(10.0, m.centsOff, 1.5)
    }

    @Test
    fun aRealStringPlayedAfterItsOvertoneStringSwitches() {
        val r = HysteresisTargetResolver()
        repeat(3) { r.resolveMatch(82.41, standard) }
        val b3 = NoteMath.frequency(59)
        val indices = (1..4).map { r.resolveMatch(b3, standard)!!.index }
        kotlin.test.assertEquals(listOf(0, 0, 4, 4), indices, "B3 wins over 'E2 third overtone' after framesToSwitch")
    }

    @Test
    fun humFarBelowTheLowestStringIsImplausible() {
        val r = HysteresisTargetResolver()
        repeat(3) { r.resolveMatch(82.41, standard) }
        kotlin.test.assertNull(r.resolveMatch(45.0, standard))
        kotlin.test.assertNull(r.resolveMatch(54.75, standard))
        kotlin.test.assertEquals(0, r.resolve(45.0, standard), "resolve keeps the current string")
        kotlin.test.assertEquals(0, r.resolveMatch(82.6, standard)!!.index, "state survives implausible readings")
    }

    @Test
    fun tuningUpTheLowEFromDIsPlausible() {
        val r = HysteresisTargetResolver()
        val m = r.resolveMatch(73.3, standard)!!
        kotlin.test.assertEquals(0, m.index)
        kotlin.test.assertEquals(1, m.fold)
        kotlin.test.assertEquals(-203.0, m.centsOff, 2.0)
    }
}

class HysteresisTargetResolverFreshAttackTest {
    private val standard = intArrayOf(40, 45, 50, 55, 59, 64).map { NoteMath.frequency(it) }.toDoubleArray()

    @Test
    fun afterResetAHighEIsPickedOnTheFirstFrameEvenThoughLowEWasCurrent() {
        val r = HysteresisTargetResolver()
        repeat(3) { r.resolveMatch(82.4, standard) }
        r.reset()
        val m = r.resolveMatch(329.6 * 1.003, standard)!!
        kotlin.test.assertEquals(5, m.index)
        kotlin.test.assertEquals(1, m.fold)
    }

    @Test
    fun withoutResetTheFourthOvertoneInterpretationHoldsForThreeFrames() {
        val r = HysteresisTargetResolver()
        repeat(3) { r.resolveMatch(82.4, standard) }
        val seen = (1..4).map { r.resolveMatch(329.6 * 1.003, standard)!!.index }
        kotlin.test.assertEquals(listOf(0, 0, 5, 5), seen)
    }
}
