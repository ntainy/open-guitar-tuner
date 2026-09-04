package dev.ntainy.guitar_tuner.data

import androidx.datastore.core.DataStore
import dev.ntainy.guitar_tuner.data.model.TunerSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json

/** Mirrors the `Json` configured in `AppContainer`. */
internal val testJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * A DataStore bound to its own scope so a test can close it and reopen the same file with a fresh instance.
 * DataStore refuses two active instances on one path, so [close] must run before the file is reopened.
 */
internal class StoreHandle<T>(val dataStore: DataStore<T>, private val scope: CoroutineScope) {
    suspend fun close() = scope.coroutineContext.job.cancelAndJoin()
}

internal fun openTuningsStore(file: File, json: Json = testJson): StoreHandle<TuningsFile> {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    return StoreHandle(createTuningsDataStore(json, scope) { file }, scope)
}

internal fun openSettingsStore(file: File, json: Json = testJson): StoreHandle<TunerSettings> {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    return StoreHandle(createSettingsDataStore(json, scope) { file }, scope)
}
