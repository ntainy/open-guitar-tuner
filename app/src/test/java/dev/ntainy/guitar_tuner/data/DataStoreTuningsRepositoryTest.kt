package dev.ntainy.guitar_tuner.data

import app.cash.turbine.test
import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup
import dev.ntainy.guitar_tuner.data.presets.PresetTunings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DataStoreTuningsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File
    private val handles = mutableListOf<StoreHandle<TuningsFile>>()
    private var now = 1_000L
    private var nextId = 1

    private class Fixture(val handle: StoreHandle<TuningsFile>, val repo: DataStoreTuningsRepository)

    @Before
    fun setUp() {
        file = File(folder.root, "tunings.json")
    }

    @After
    fun tearDown() = runBlocking {
        handles.forEach { it.close() }
    }

    private fun open(): Fixture {
        val handle = openTuningsStore(file).also(handles::add)
        val repo = DataStoreTuningsRepository(
            dataStore = handle.dataStore,
            clock = { now++ },
            idFactory = { "custom_${nextId++}" },
        )
        return Fixture(handle, repo)
    }

    private fun custom(id: String, name: String, strings: List<Int> = listOf(40, 45, 50, 55, 59, 64)): Tuning =
        Tuning(id = id, name = name, strings = strings)

    @Test
    fun `fresh store lists presets only`() = runTest {
        open().repo.tunings.test {
            assertEquals(PresetTunings.all, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert puts the tuning first and survives reopening the file`() = runTest {
        val first = open()
        first.repo.upsert(custom("custom_a", "Mine"))

        val list = first.repo.tunings.first()
        val stored = list.first()
        assertEquals("custom_a", stored.id)
        assertEquals("Mine", stored.name)
        assertEquals(TuningGroup.MINE, stored.group)
        assertFalse(stored.isPreset)
        assertEquals(1_000L, stored.createdAt)
        assertEquals(PresetTunings.all, list.drop(1))

        first.handle.close()
        val second = open()
        assertEquals(list, second.repo.tunings.first())
    }

    @Test
    fun `upsert with the same id replaces and keeps the original timestamp`() = runTest {
        val repo = open().repo
        repo.upsert(custom("custom_a", "Mine"))
        repo.upsert(custom("custom_b", "Other"))
        repo.upsert(custom("custom_a", "Renamed", strings = listOf(38, 45, 50, 55, 59, 64)))

        val customs = repo.tunings.first().filterNot { it.isPreset }
        assertEquals(listOf("custom_a", "custom_b"), customs.map { it.id })
        assertEquals("Renamed", customs[0].name)
        assertEquals(38, customs[0].strings.first())
        assertEquals(1_000L, customs[0].createdAt)
    }

    @Test
    fun `custom tunings are ordered by createdAt then id`() = runTest {
        val repo = open().repo
        repo.upsert(custom("custom_b", "Second"))
        repo.upsert(custom("custom_a", "Third"))
        repo.upsert(custom("custom_z", "Oldest").copy(createdAt = 5L))
        repo.upsert(custom("custom_y", "Oldest twin").copy(createdAt = 5L))

        val customs = repo.tunings.first().filterNot { it.isPreset }
        assertEquals(listOf("custom_y", "custom_z", "custom_b", "custom_a"), customs.map { it.id })
    }

    @Test
    fun `upsert forces MINE group and clears the preset flag`() = runTest {
        val repo = open().repo
        repo.upsert(custom("custom_a", "Mine").copy(group = TuningGroup.OPEN))

        val stored = repo.tunings.first().first()
        assertEquals(TuningGroup.MINE, stored.group)
        assertFalse(stored.isPreset)
    }

    @Test
    fun `presets are rejected`() = runTest {
        val repo = open().repo
        assertFailsWith<IllegalArgumentException> { repo.upsert(PresetTunings.all.first()) }
        assertFailsWith<IllegalArgumentException> { repo.upsert(custom(PresetIds.PREFIX + "sneaky", "Sneaky")) }
        assertFailsWith<IllegalArgumentException> { repo.upsert(custom("custom_a", "Flagged").copy(isPreset = true)) }
        assertEquals(PresetTunings.all, repo.tunings.first())
    }

    @Test
    fun `invalid tunings are rejected with the validation message`() = runTest {
        val repo = open().repo
        val fiveStrings = assertFailsWith<IllegalArgumentException> {
            repo.upsert(custom("custom_a", "Short", strings = listOf(40, 45, 50, 55, 59)))
        }
        assertTrue(fiveStrings.message.orEmpty().contains("exactly 6 strings"), fiveStrings.message)
        assertFailsWith<IllegalArgumentException> { repo.upsert(custom("custom_a", "   ")) }
        assertFailsWith<IllegalArgumentException> {
            repo.upsert(custom("custom_a", "Too low", strings = listOf(10, 45, 50, 55, 59, 64)))
        }
        assertEquals(PresetTunings.all, repo.tunings.first())
    }

    @Test
    fun `delete removes a custom tuning and ignores presets and unknown ids`() = runTest {
        val repo = open().repo
        repo.upsert(custom("custom_a", "Mine"))
        repo.upsert(custom("custom_b", "Other"))

        repo.delete("custom_a")
        repo.delete(PresetIds.STANDARD)
        repo.delete("custom_missing")

        val list = repo.tunings.first()
        assertEquals(listOf("custom_b"), list.filterNot { it.isPreset }.map { it.id })
        assertEquals(PresetTunings.all, list.filter { it.isPreset })
    }

    @Test
    fun `duplicate names copies copy, copy 2, copy 3`() = runTest {
        val repo = open().repo
        val first = repo.duplicate(PresetIds.STANDARD)
        val second = repo.duplicate(PresetIds.STANDARD)
        val third = repo.duplicate(PresetIds.STANDARD)
        val named = repo.duplicate(PresetIds.STANDARD, "Road tuning")

        assertEquals("Standard copy", first.name)
        assertEquals("Standard copy 2", second.name)
        assertEquals("Standard copy 3", third.name)
        assertEquals("Road tuning", named.name)
        assertEquals(listOf(first, second, third, named), repo.tunings.first().filterNot { it.isPreset })
    }

    @Test
    fun `duplicate of a preset lands in MINE with a fresh id`() = runTest {
        val repo = open().repo
        val standard = checkNotNull(PresetTunings.byId(PresetIds.STANDARD))
        val copy = repo.duplicate(PresetIds.STANDARD)

        assertEquals("custom_1", copy.id)
        assertEquals(TuningGroup.MINE, copy.group)
        assertFalse(copy.isPreset)
        assertEquals(1_000L, copy.createdAt)
        assertEquals(standard.strings, copy.strings)
        assertEquals(copy, repo.tuning("custom_1").first())
        assertEquals(standard, repo.tuning(PresetIds.STANDARD).first())
    }

    @Test
    fun `duplicate of a custom tuning copies its strings`() = runTest {
        val repo = open().repo
        repo.upsert(custom("custom_a", "Mine", strings = listOf(36, 43, 48, 53, 57, 62)))
        val copy = repo.duplicate("custom_a")

        assertEquals("Mine copy", copy.name)
        assertEquals(listOf(36, 43, 48, 53, 57, 62), copy.strings)
        assertEquals(listOf("custom_a", copy.id), repo.tunings.first().filterNot { it.isPreset }.map { it.id })
    }

    @Test
    fun `duplicate of an unknown id throws`() = runTest {
        val repo = open().repo
        assertFailsWith<NoSuchElementException> { repo.duplicate("custom_missing") }
        assertEquals(PresetTunings.all, repo.tunings.first())
    }

    @Test
    fun `corrupt file reads as presets only and is replaced on the next write`() = runTest {
        file.writeText("{ this is not json")
        val repo = open().repo

        assertEquals(PresetTunings.all, repo.tunings.first())

        repo.upsert(custom("custom_a", "Mine"))
        assertEquals("custom_a", repo.tunings.first().first().id)
        val onDisk = testJson.decodeFromString(TuningsFile.serializer(), file.readText())
        assertEquals(TuningsFile.CURRENT_VERSION, onDisk.version)
        assertEquals(listOf("custom_a"), onDisk.tunings.map { it.id })
    }

    @Test
    fun `tuning flow follows updates`() = runTest {
        val repo = open().repo
        repo.tuning("custom_a").test {
            assertNull(awaitItem())
            repo.upsert(custom("custom_a", "Mine"))
            assertEquals("Mine", awaitItem()?.name)
            repo.delete("custom_a")
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `file stores custom tunings only`() = runTest {
        val repo = open().repo
        repo.upsert(custom("custom_a", "Mine"))
        repo.duplicate(PresetIds.STANDARD)

        val onDisk = testJson.decodeFromString(TuningsFile.serializer(), file.readText())
        assertEquals(TuningsFile.CURRENT_VERSION, onDisk.version)
        assertEquals(listOf("custom_a", "custom_1"), onDisk.tunings.map { it.id })
        assertTrue(onDisk.tunings.none { it.isPreset })
    }
}
