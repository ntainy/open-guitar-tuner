package dev.ntainy.guitar_tuner.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackPolicyTest {

    private fun stack(vararg keys: NavKey): MutableList<NavKey> = keys.toMutableList()

    @Test
    fun aTabSitsOnTopOfTuneSoBackReturnsToTune() {
        val s = stack(TuneKey)
        s.switchTo(SettingsKey)
        assertEquals(listOf(TuneKey, SettingsKey), s)
    }

    @Test
    fun switchingBetweenTabsReplacesInsteadOfStacking() {
        val s = stack(TuneKey, SettingsKey)
        s.switchTo(TuningsKey())
        assertEquals(listOf(TuneKey, TuningsKey()), s)
        s.switchTo(SettingsKey)
        assertEquals(listOf(TuneKey, SettingsKey), s)
    }

    @Test
    fun tuneTabClearsBackToTheRoot() {
        val s = stack(TuneKey, TuningsKey(pick = true))
        s.switchTo(TuneKey)
        assertEquals(listOf<NavKey>(TuneKey), s)
    }

    @Test
    fun tappingTheCurrentTabChangesNothing() {
        val s = stack(TuneKey, SettingsKey)
        s.switchTo(SettingsKey)
        assertEquals(listOf(TuneKey, SettingsKey), s)
        val root = stack(TuneKey)
        root.switchTo(TuneKey)
        assertEquals(listOf<NavKey>(TuneKey), root)
    }

    @Test
    fun theTunesTabLeavesPickerModeBehind() {
        val s = stack(TuneKey, TuningsKey(pick = true))
        s.switchTo(TuningsKey())
        assertEquals(listOf(TuneKey, TuningsKey()), s)
    }
}
