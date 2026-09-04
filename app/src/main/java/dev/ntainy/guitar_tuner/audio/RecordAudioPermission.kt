package dev.ntainy.guitar_tuner.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Snapshot of the `RECORD_AUDIO` permission for the Tune screen.
 *
 * @property granted true when the app may record
 * @property shouldShowRationale true when the user denied once and Android suggests explaining why before
 *   asking again; after a permanent denial both flags are false and the user must go to system settings
 * @property request launches the system permission dialog
 */
@Stable
class RecordAudioPermissionState(
    val granted: Boolean,
    val shouldShowRationale: Boolean,
    val request: () -> Unit,
)

/**
 * Tracks the `RECORD_AUDIO` permission and offers a way to request it.
 *
 * The state refreshes when the dialog closes and on every `ON_RESUME`, so coming back from system settings
 * is picked up. [onResult] is invoked with the current value on first composition and again whenever it
 * changes; feeding it to [TunerEngine.onPermissionResult] is all the screen has to do.
 */
@Composable
fun rememberRecordAudioPermissionState(onResult: (Boolean) -> Unit = {}): RecordAudioPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val latestOnResult by rememberUpdatedState(onResult)
    var granted by remember { mutableStateOf(context.hasRecordAudioPermission()) }
    var rationale by remember { mutableStateOf(activity.shouldShowRecordAudioRationale()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        rationale = activity.shouldShowRecordAudioRationale()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasRecordAudioPermission()
        rationale = activity.shouldShowRecordAudioRationale()
    }
    LaunchedEffect(granted) { latestOnResult(granted) }

    return remember(granted, rationale, launcher) {
        RecordAudioPermissionState(
            granted = granted,
            shouldShowRationale = rationale,
            request = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        )
    }
}

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun Activity?.shouldShowRecordAudioRationale(): Boolean =
    this?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: false
