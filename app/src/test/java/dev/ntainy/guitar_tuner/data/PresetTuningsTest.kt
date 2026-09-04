package dev.ntainy.guitar_tuner.data

import dev.ntainy.guitar_tuner.data.model.MAX_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.MIN_STRING_MIDI
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.STRING_COUNT
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.data.presets.PresetTunings
import dev.ntainy.guitar_tuner.dsp.NoteMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetTuningsTest {

    // The catalog in docs/PLAN.md lists 1 + 7 + 6 + 6 + 3 presets.
    @Test
    fun `catalog has every preset from the plan`() {
        assertEquals(23, PresetTunings.all.size)
        val perGroup = PresetTunings.all.groupingBy { it.group }.eachCount()
        assertEquals(
            mapOf(
                TuningGroup.STANDARD to 1,
                TuningGroup.POWER to 7,
                TuningGroup.TRANSPOSED to 6,
                TuningGroup.OPEN to 6,
                TuningGroup.EXTRAS to 3,
            ),
            perGroup,
        )
    }

    @Test
    fun `ids and names are unique`() {
        val ids = PresetTunings.all.map { it.id }
        val names = PresetTunings.all.map { it.name }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids in $ids")
        assertEquals(names.size, names.toSet().size, "duplicate names in $names")
    }

    @Test
    fun `every id carries the preset prefix and Standard has the well-known id`() {
        PresetTunings.all.forEach { assertTrue(it.id.startsWith(PresetIds.PREFIX), "id ${it.id}") }
        assertEquals(PresetIds.STANDARD, PresetTunings.all.first().id)
        assertEquals("Standard", PresetTunings.all.first().name)
        assertEquals(listOf(40, 45, 50, 55, 59, 64), PresetTunings.all.first().strings)
    }

    @Test
    fun `every preset is flagged as preset and valid`() {
        PresetTunings.all.forEach { tuning ->
            assertTrue(tuning.isPreset, tuning.name)
            assertTrue(tuning.group != TuningGroup.MINE, tuning.name)
            assertEquals(0L, tuning.createdAt, tuning.name)
            assertEquals(STRING_COUNT, tuning.strings.size, tuning.name)
            tuning.strings.forEach { midi -> assertTrue(midi in MIN_STRING_MIDI..MAX_STRING_MIDI, "${tuning.name}: $midi") }
            assertTrue(tuning.isValid, "${tuning.name}: ${tuning.validationErrors()}")
            assertTrue(tuning.subtitle.isNotBlank(), "${tuning.name} has no subtitle")
        }
    }

    @Test
    fun `strings never descend from low to high`() {
        PresetTunings.all.forEach { tuning ->
            assertTrue(tuning.strings.zipWithNext().all { (low, high) -> low <= high }, "${tuning.name}: ${tuning.strings}")
        }
    }

    @Test
    fun `groups appear in catalog order`() {
        assertEquals(
            listOf(TuningGroup.STANDARD, TuningGroup.POWER, TuningGroup.TRANSPOSED, TuningGroup.OPEN, TuningGroup.EXTRAS),
            PresetTunings.all.map { it.group }.distinct(),
        )
    }

    @Test
    fun `Drop D spells D2 A2 D3 G3 B3 E4`() {
        val dropD = assertNotNull(PresetTunings.byId(PresetIds.PREFIX + "drop_d"))
        assertEquals(listOf("D2", "A2", "D3", "G3", "B3", "E4"), dropD.strings.map { NoteMath.name(it) })
    }

    @Test
    fun `byId finds presets and nothing else`() {
        assertEquals(PresetTunings.all.first(), PresetTunings.byId(PresetIds.STANDARD))
        PresetTunings.all.forEach { assertEquals(it, PresetTunings.byId(it.id)) }
        assertNull(PresetTunings.byId("custom_123"))
        assertNull(PresetTunings.byId(""))
    }
}
