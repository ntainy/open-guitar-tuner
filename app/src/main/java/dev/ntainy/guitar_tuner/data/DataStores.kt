package dev.ntainy.guitar_tuner.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** File name of the custom tunings store inside the app's DataStore directory (`files/datastore/`). */
const val TUNINGS_FILE_NAME = "tunings.json"

/** File name of the settings store inside the app's DataStore directory (`files/datastore/`). */
const val SETTINGS_FILE_NAME = "settings.json"

/**
 * Typed DataStore [Serializer] that stores [T] as a UTF-8 JSON document.
 *
 * Unknown keys are ignored regardless of how [json] was configured, so a document written by a newer build still
 * loads. Anything that is not a valid document for [serializer] (garbage, an empty file, an unknown enum constant) is
 * reported as a [CorruptionException], which lets a [ReplaceFileCorruptionHandler] fall back to [defaultValue].
 *
 * @param json formatting and leniency options; `encodeDefaults` and `prettyPrint` are honoured on write.
 * @param serializer the kotlinx.serialization strategy for [T].
 * @param default the value DataStore reports while the file does not exist yet.
 */
class JsonSerializer<T>(
    json: Json,
    private val serializer: KSerializer<T>,
    default: T,
) : Serializer<T> {
    private val parser: Json = Json(from = json) { ignoreUnknownKeys = true }

    override val defaultValue: T = default

    override suspend fun readFrom(input: InputStream): T {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) throw CorruptionException("Empty JSON document")
        return try {
            parser.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            // SerializationException (and its JSON subclasses) extend IllegalArgumentException.
            throw CorruptionException("Cannot parse JSON document", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(parser.encodeToString(serializer, t).encodeToByteArray())
    }
}

/**
 * DataStore for the user's custom tunings, stored as [TUNINGS_FILE_NAME] under [Context.dataStoreFile].
 *
 * A corrupt file is replaced by an empty [TuningsFile]. Create at most one instance per file for the life of the
 * process; DataStore refuses a second active instance on the same path.
 *
 * @param scope owns the store's coroutines. Its [kotlinx.coroutines.Job] bounds the store's lifetime; file IO always
 * runs on [Dispatchers.IO] no matter which dispatcher the scope carries.
 */
fun createTuningsDataStore(context: Context, json: Json, scope: CoroutineScope): DataStore<TuningsFile> =
    createTuningsDataStore(json, scope) { context.dataStoreFile(TUNINGS_FILE_NAME) }

/** [createTuningsDataStore] for an explicit file, for example a temporary directory in a JVM test. */
fun createTuningsDataStore(json: Json, scope: CoroutineScope, produceFile: () -> File): DataStore<TuningsFile> =
    createJsonDataStore(json, TuningsFile.serializer(), TuningsFile(), scope, produceFile)

/**
 * DataStore for [TunerSettings], stored as [SETTINGS_FILE_NAME] under [Context.dataStoreFile].
 *
 * A corrupt file is replaced by default settings. The same single-instance and dispatcher rules as
 * [createTuningsDataStore] apply.
 */
fun createSettingsDataStore(context: Context, json: Json, scope: CoroutineScope): DataStore<TunerSettings> =
    createSettingsDataStore(json, scope) { context.dataStoreFile(SETTINGS_FILE_NAME) }

/** [createSettingsDataStore] for an explicit file, for example a temporary directory in a JVM test. */
fun createSettingsDataStore(json: Json, scope: CoroutineScope, produceFile: () -> File): DataStore<TunerSettings> =
    createJsonDataStore(json, TunerSettings.serializer(), TunerSettings(), scope, produceFile)

/**
 * Builds a JSON-backed typed DataStore for any serializable [T].
 *
 * The file is produced lazily by [produceFile]; a corrupt document is replaced with [default]. Reads and writes run
 * on [Dispatchers.IO] within [scope]'s job.
 */
fun <T> createJsonDataStore(
    json: Json,
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope,
    produceFile: () -> File,
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonSerializer(json, serializer, default),
    corruptionHandler = ReplaceFileCorruptionHandler { default },
    scope = scope + Dispatchers.IO,
    produceFile = produceFile,
)
