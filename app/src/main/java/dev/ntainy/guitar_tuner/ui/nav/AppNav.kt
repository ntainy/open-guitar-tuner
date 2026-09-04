package dev.ntainy.guitar_tuner.ui.nav

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.ntainy.guitar_tuner.di.AppContainer
import dev.ntainy.guitar_tuner.ui.editor.TuningEditorScreen
import dev.ntainy.guitar_tuner.ui.settings.SettingsScreen
import dev.ntainy.guitar_tuner.ui.tuner.TunerScreen
import dev.ntainy.guitar_tuner.ui.tunings.TuningsScreen
import kotlinx.serialization.Serializable

@Serializable
data object TuneKey : NavKey

@Serializable
data object TuningsKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** Editor for an existing custom tuning, or a new one when [tuningId] is null. */
@Serializable
data class EditorKey(val tuningId: String? = null) : NavKey

private enum class Tab(val key: NavKey, val label: String, val icon: ImageVector) {
    TUNE(TuneKey, "Tune", Icons.Outlined.GraphicEq),
    TUNINGS(TuningsKey, "Tunings", Icons.Outlined.LibraryMusic),
    SETTINGS(SettingsKey, "Settings", Icons.Outlined.Settings),
}

@Composable
fun AppNav(container: AppContainer) {
    val backStack = rememberNavBackStack(TuneKey)
    val current = backStack.lastOrNull()
    val showBar = Tab.entries.any { it.key == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.key,
                            onClick = { backStack.switchTo(tab.key) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<TuneKey> {
                    TunerScreen(container = container, onOpenTunings = { backStack.switchTo(TuningsKey) })
                }
                entry<TuningsKey> {
                    TuningsScreen(container = container, onEditTuning = { id -> backStack.add(EditorKey(id)) })
                }
                entry<SettingsKey> {
                    SettingsScreen(container = container)
                }
                entry<EditorKey> { key ->
                    TuningEditorScreen(container = container, tuningId = key.tuningId, onDone = { backStack.removeLastOrNull() })
                }
            },
        )
    }
}

/** Top-level tabs replace the stack so the system back gesture always leaves the app from a tab. */
private fun NavBackStack<NavKey>.switchTo(key: NavKey) {
    if (lastOrNull() == key && size == 1) return
    clear()
    add(key)
}
