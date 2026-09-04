package dev.ntainy.guitar_tuner.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ntainy.guitar_tuner.ui.theme.target

/** Friendly stand-in for the tuner while RECORD_AUDIO is not granted. */
@Composable
fun MicPermissionGate(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(scheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MicOff,
                contentDescription = null,
                tint = scheme.target,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = "Microphone access needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (permanentlyDenied) {
                "Microphone access is turned off for GuitarTuner. Enable it in system settings to start tuning."
            } else {
                "GuitarTuner listens to your guitar through the microphone or a USB interface. " +
                    "Nothing is recorded or stored."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text("Open settings") }
            TextButton(onClick = onAllow) { Text("Try again") }
        } else {
            Button(onClick = onAllow) { Text("Allow") }
        }
    }
}
