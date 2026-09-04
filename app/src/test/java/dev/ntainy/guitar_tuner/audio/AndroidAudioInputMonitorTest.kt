package dev.ntainy.guitar_tuner.audio

import android.media.AudioDeviceInfo
import dev.ntainy.guitar_tuner.audio.AndroidAudioInputMonitor.Companion.arrange
import dev.ntainy.guitar_tuner.audio.AndroidAudioInputMonitor.Companion.toInputDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The Android-free half of the monitor: device mapping and list ordering. */
class AndroidAudioInputMonitorTest {

    @Test
    fun builtinMicMapsToTheStableBuiltinKey() {
        val device = assertNotNull(toInputDevice(AudioDeviceInfo.TYPE_BUILTIN_MIC, 7, "Pixel"))
        assertEquals(AudioInputDevice(7, AudioInputDevice.BUILTIN_KEY, "Built-in microphone", InputKind.BUILTIN_MIC), device)
    }

    @Test
    fun usbTypesMapToProductNameKeys() {
        listOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
            .forEach { type ->
                val device = assertNotNull(toInputDevice(type, 42, " NUX MP-3 "))
                assertEquals(AudioInputDevice(42, "usb:NUX MP-3", "NUX MP-3 (USB)", InputKind.USB), device)
            }
        assertEquals("usb:USB audio", assertNotNull(toInputDevice(AudioDeviceInfo.TYPE_USB_DEVICE, 1, null)).key)
    }

    @Test
    fun headsetsMapToTheirKinds() {
        assertEquals(InputKind.WIRED_HEADSET, assertNotNull(toInputDevice(AudioDeviceInfo.TYPE_WIRED_HEADSET, 3, "x")).kind)
        assertEquals(InputKind.BLUETOOTH, assertNotNull(toInputDevice(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 4, "Buds")).kind)
        assertEquals(InputKind.BLUETOOTH, assertNotNull(toInputDevice(AudioDeviceInfo.TYPE_BLE_HEADSET, 5, "Buds")).kind)
        assertEquals("Buds (Bluetooth)", assertNotNull(toInputDevice(AudioDeviceInfo.TYPE_BLE_HEADSET, 5, "Buds")).name)
    }

    @Test
    fun nonCaptureTypesAreSkipped() {
        listOf(
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
            AudioDeviceInfo.TYPE_FM_TUNER,
            AudioDeviceInfo.TYPE_TV_TUNER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_UNKNOWN,
        ).forEach { type -> assertNull(toInputDevice(type, 1, "x"), "type $type should be skipped") }
    }

    @Test
    fun arrangePutsUsbFirstCollapsesBuiltinMicsAndAppendsTestTone() {
        val mic1 = AudioInputDevice(2, AudioInputDevice.BUILTIN_KEY, "Built-in microphone", InputKind.BUILTIN_MIC)
        val mic2 = mic1.copy(id = 3)
        val wired = AudioInputDevice(5, "wired", "Wired headset", InputKind.WIRED_HEADSET)
        val usb = AudioInputDevice(9, "usb:NUX MP-3", "NUX MP-3 (USB)", InputKind.USB)

        assertEquals(listOf(usb, mic1, wired), arrange(listOf(mic1, wired, mic2, usb), includeTestTone = false))
        assertEquals(
            listOf(usb, mic1, wired, SyntheticToneSource.DEVICE),
            arrange(listOf(mic1, wired, mic2, usb), includeTestTone = true),
        )
        assertEquals(listOf(SyntheticToneSource.DEVICE), arrange(emptyList(), includeTestTone = true))
    }
}
