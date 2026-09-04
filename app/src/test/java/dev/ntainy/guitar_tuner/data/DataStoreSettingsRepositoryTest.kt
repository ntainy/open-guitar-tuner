package dev.ntainy.guitar_tuner.data

import app.cash.turbine.test
import dev.ntainy.guitar_tuner.data.model.HeadstockLayout
import dev.ntainy.guitar_tuner.data.model.InputPolicy
import dev.ntainy.guitar_tuner.data.model.ThemeMode
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import dev.ntainy.guitar_tuner.dsp.Notation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File
    private val handles = mutableListOf<StoreHandle<TunerSettings>>()

    private class Fixture(val handle: StoreHandle<TunerSettings>, val repo: DataStoreSettingsRepository)

    @Before
    fun setUp() {
        file = File(folder.root, "settings.json")
    }

    @After
    fun tearDown() = runBlocking {
        handles.forEach { it.close() }
    }

    private fun open(): Fixture {
        val handle = openSettingsStore(file).also(handles::add)
        return Fixture(handle, DataStoreSettingsRepository(handle.dataStore))
    }

    @Test
    fun `fresh store yields defaults`() = runTest {
        assertEquals(TunerSettings(), open().repo.settings.first())
    }

    @Test
    fun `update persists across instances`() = runTest {
        val first = open()
        first.repo.update {
            it.copy(
                a4Hz = 442.0,
                toleranceCents = 5.0,
                inputPolicy = InputPolicy.SPECIFIC_DEVICE,
                preferredInputKey = "usb:MP-3",
                notation = Notation.FLATS,
                headstockLayout = HeadstockLayout.SIX_IN_LINE,
                keepScreenOn = false,
                theme = ThemeMode.LIGHT,
                showHz = false,
                activeTuningId = "custom_x",
            )
        }
        val expected = first.repo.settings.first()
        assertEquals(442.0, expected.a4Hz)
        assertEquals("custom_x", expected.activeTuningId)

        first.handle.close()
        assertEquals(expected, open().repo.settings.first())
    }

    @Test
    fun `settings flow emits every update`() = runTest {
        val repo = open().repo
        repo.settings.test {
            assertEquals(TunerSettings(), awaitItem())
            repo.update { it.copy(showHz = false) }
            assertFalse(awaitItem().showHz)
            repo.update { it.copy(theme = ThemeMode.SYSTEM) }
            assertEquals(ThemeMode.SYSTEM, awaitItem().theme)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update clamps a4Hz and tolerance`() = runTest {
        val repo = open().repo

        repo.update { it.copy(a4Hz = 1_000.0, toleranceCents = 50.0) }
        assertEquals(TunerSettings.MAX_A4_HZ, repo.settings.first().a4Hz)
        assertEquals(TunerSettings.MAX_TOLERANCE_CENTS, repo.settings.first().toleranceCents)

        repo.update { it.copy(a4Hz = 100.0, toleranceCents = 0.0) }
        assertEquals(TunerSettings.MIN_A4_HZ, repo.settings.first().a4Hz)
        assertEquals(TunerSettings.MIN_TOLERANCE_CENTS, repo.settings.first().toleranceCents)

        repo.update { it.copy(a4Hz = 432.0, toleranceCents = 2.5) }
        assertEquals(432.0, repo.settings.first().a4Hz)
        assertEquals(2.5, repo.settings.first().toleranceCents)

        repo.update { it.copy(a4Hz = Double.NaN, toleranceCents = Double.NaN) }
        assertEquals(TunerSettings().a4Hz, repo.settings.first().a4Hz)
        assertEquals(TunerSettings().toleranceCents, repo.settings.first().toleranceCents)
    }

    @Test
    fun `corrupt file reads as defaults and accepts writes`() = runTest {
        file.writeText("<<not json>>")
        val repo = open().repo

        assertEquals(TunerSettings(), repo.settings.first())

        repo.update { it.copy(a4Hz = 441.0) }
        assertEquals(441.0, repo.settings.first().a4Hz)
        assertEquals(441.0, testJson.decodeFromString(TunerSettings.serializer(), file.readText()).a4Hz)
    }

    @Test
    fun `unknown fields in the file are ignored`() = runTest {
        file.writeText("""{"a4Hz": 442.0, "futureField": {"nested": true}, "anotherOne": [1, 2, 3]}""")
        val settings = open().repo.settings.first()

        assertEquals(TunerSettings(a4Hz = 442.0), settings)
    }

    @Test
    fun `out-of-range values in the file are clamped on read`() = runTest {
        file.writeText("""{"a4Hz": 900.0, "toleranceCents": 0.1}""")
        val settings = open().repo.settings.first()

        assertEquals(TunerSettings.MAX_A4_HZ, settings.a4Hz)
        assertEquals(TunerSettings.MIN_TOLERANCE_CENTS, settings.toleranceCents)
    }
}
