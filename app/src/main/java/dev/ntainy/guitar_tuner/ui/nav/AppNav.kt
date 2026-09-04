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

/**
 * The tunings list. Opened from the Tune screen's tuning chip with [pick] set: the user came to choose, so choosing
 * pops straight back to Tune. From the bottom bar it is an ordinary tab.
 */
@Serializable
data class TuningsKey(val pick: Boolean = false) : NavKey

@Serializable
data object SettingsKey : NavKey

/** Editor for an existing custom tuning, or a new one when [tuningId] is null. */
@Serializable
data class EditorKey(val tuningId: String? = null) : NavKey

private enum class Tab(val label: String, val icon: ImageVector) {
    TUNE("Tune", Icons.Outlined.GraphicEq),
    TUNINGS("Tunings", Icons.Outlined.LibraryMusic),
    SETTINGS("Settings", Icons.Outlined.Settings),
    ;

    /** The key the bar navigates to; the Tunings tab is never the picker. */
    val key: NavKey
        get() = when (this) {
            TUNE -> TuneKey
            TUNINGS -> TuningsKey()
            SETTINGS -> SettingsKey
        }

    /** Whether [key] is this tab's screen, in either of its forms. */
    fun matches(key: NavKey?): Boolean = when (this) {
        TUNE -> key is TuneKey
        TUNINGS -> key is TuningsKey
        SETTINGS -> key is SettingsKey
    }
}

@Composable
fun AppNav(container: AppContainer) {
    val backStack = rememberNavBackStack(TuneKey)
    val current = backStack.lastOrNull()
    val currentTab = Tab.entries.firstOrNull { it.matches(current) }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
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
                    TunerScreen(container = container, onOpenTunings = { backStack.add(TuningsKey(pick = true)) })
                }
                entry<TuningsKey> { key ->
                    TuningsScreen(
                        container = container,
                        onEditTuning = { id -> backStack.add(EditorKey(id)) },
                        onPicked = if (key.pick) ({ backStack.removeLastOrNull() }) else null,
                    )
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

/**
 * Tune is the root. The other tabs sit on top of it, one at a time, so the system back gesture returns to Tune
 * from any tab and leaves the app only from Tune. Switching between the other two replaces rather than stacks,
 * so back never walks through a history of tab taps.
 */
internal fun MutableList<NavKey>.switchTo(key: NavKey) {
    if (key is TuneKey) {
        if (size == 1 && first() is TuneKey) return
        clear()
        add(TuneKey)
        return
    }
    if (size == 2 && first() is TuneKey && last() == key) return
    clear()
    add(TuneKey)
    add(key)
}
