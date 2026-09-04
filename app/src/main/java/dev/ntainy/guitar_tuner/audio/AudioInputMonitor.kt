package dev.ntainy.guitar_tuner.audio

import kotlinx.coroutines.flow.Flow

/**
 * Lists usable input devices and re-emits whenever one is plugged or unplugged.
 *
 * Wave 1 implementation wraps `AudioManager.getDevices(GET_DEVICES_INPUTS)` + `AudioDeviceCallback`.
 * Debug builds append the synthetic test-tone device.
 */
interface AudioInputMonitor {
    val devices: Flow<List<AudioInputDevice>>
}
