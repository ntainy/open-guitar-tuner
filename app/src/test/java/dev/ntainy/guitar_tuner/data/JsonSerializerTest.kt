package dev.ntainy.guitar_tuner.data

import androidx.datastore.core.CorruptionException
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class JsonSerializerTest {

    private val strictJson = Json
    private val serializer = JsonSerializer(strictJson, TunerSettings.serializer(), TunerSettings())

    private suspend fun read(text: String): TunerSettings =
        serializer.readFrom(ByteArrayInputStream(text.encodeToByteArray()))

    @Test
    fun `round trips a document`() = runTest {
        val value = TunerSettings(a4Hz = 442.0, showHz = false, activeTuningId = "custom_1")
        val bytes = ByteArrayOutputStream().also { serializer.writeTo(value, it) }.toByteArray()

        assertEquals(value, serializer.readFrom(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `ignores unknown keys even with a strict Json`() = runTest {
        assertEquals(TunerSettings(a4Hz = 442.0), read("""{"a4Hz": 442.0, "unknown": "yes"}"""))
    }

    @Test
    fun `reports garbage and empty input as corruption`() = runTest {
        assertFailsWith<CorruptionException> { read("garbage") }
        assertFailsWith<CorruptionException> { read("") }
        assertFailsWith<CorruptionException> { read("   \n") }
        assertFailsWith<CorruptionException> { read("""{"theme": "NEON"}""") }
        assertFailsWith<CorruptionException> { read("""{"a4Hz": "loud"}""") }
    }

    @Test
    fun `exposes the default value`() {
        assertEquals(TunerSettings(), serializer.defaultValue)
    }
}
