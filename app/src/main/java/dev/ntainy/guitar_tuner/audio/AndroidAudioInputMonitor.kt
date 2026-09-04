package dev.ntainy.guitar_tuner.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ntainy.guitar_tuner.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach

/**
 * [AudioInputMonitor] backed by [AudioManager]: lists the capture-capable devices and re-emits on every
 * hot-plug event reported through [AudioDeviceCallback].
 *
 * Built-in microphones are collapsed into one entry (phones expose several), USB interfaces are listed first
 * so the preferred-USB policy has an obvious pick, and debug builds append the synthetic test-tone device.
 * Every distinct list is logged with tag [TAG]; `adb logcat -s AudioInputMonitor` shows hot-plug activity.
 *
 * @param includeTestTone append [SyntheticToneSource.DEVICE] to every list (default: debug builds only)
 */
class AndroidAudioInputMonitor(
    context: Context,
    private val includeTestTone: Boolean = BuildConfig.DEBUG,
) : AudioInputMonitor {

    private val audioManager: AudioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    override val devices: Flow<List<AudioInputDevice>> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(snapshot())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(snapshot())
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        trySend(snapshot())
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
        .distinctUntilChanged()
        .onEach { list ->
            Log.d(TAG, "Inputs (${list.size}): " + list.joinToString { "${it.name} [${it.key}, id=${it.id}]" })
        }

    private fun snapshot(): List<AudioInputDevice> {
        val mapped = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource }
            .mapNotNull { toInputDevice(it.type, it.id, it.productName) }
        return arrange(mapped, includeTestTone)
    }

    companion object {
        const val TAG = "AudioInputMonitor"
        const val BUILTIN_NAME = "Built-in microphone"

        /**
         * Maps one [AudioDeviceInfo] (already known to be a source) to the tuner's device model, or null for
         * types that cannot be used as a guitar input (telephony, remote submix, FM/TV tuners, ...).
         */
        internal fun toInputDevice(type: Int, id: Int, productName: CharSequence?): AudioInputDevice? {
            val product = productName?.toString()?.trim().orEmpty()
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC ->
                    AudioInputDevice(id, AudioInputDevice.BUILTIN_KEY, BUILTIN_NAME, InputKind.BUILTIN_MIC)

                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                -> {
                    val name = product.ifEmpty { "USB audio" }
                    AudioInputDevice(id, "usb:$name", "$name (USB)", InputKind.USB)
                }

                AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    AudioInputDevice(id, "wired", "Wired headset", InputKind.WIRED_HEADSET)

                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                -> {
                    val name = product.ifEmpty { "Bluetooth headset" }
                    AudioInputDevice(id, "bt:$name", "$name (Bluetooth)", InputKind.BLUETOOTH)
                }

                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_DOCK,
                AudioDeviceInfo.TYPE_DOCK_ANALOG,
                -> {
                    val name = product.ifEmpty { "Line in" }
                    AudioInputDevice(id, "other:$type:$name", name, InputKind.OTHER)
                }

                else -> null
            }
        }

        /**
         * Orders USB first, then the built-in mic, then everything else; collapses duplicate keys (several
         * built-in mics become one entry, the first id wins) and appends the test tone when requested.
         */
        internal fun arrange(devices: List<AudioInputDevice>, includeTestTone: Boolean): List<AudioInputDevice> {
            val ordered = devices.sortedBy { rank(it.kind) }.distinctBy { it.key }
            return if (includeTestTone) ordered + SyntheticToneSource.DEVICE else ordered
        }

        private fun rank(kind: InputKind): Int = when (kind) {
            InputKind.USB -> 0
            InputKind.BUILTIN_MIC -> 1
            InputKind.WIRED_HEADSET -> 2
            InputKind.BLUETOOTH -> 3
            InputKind.OTHER -> 4
            InputKind.TEST_TONE -> 5
        }
    }
}
