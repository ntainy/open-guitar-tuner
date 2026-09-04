package dev.ntainy.guitar_tuner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.di.AppContainer

/** M0 placeholder. Wave 2 (agent E) replaces this file with the settings screen. */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Settings", style = MaterialTheme.typography.displayMedium)
        Text("Settings arrive in Wave 2.", style = MaterialTheme.typography.bodyLarge)
    }
}
