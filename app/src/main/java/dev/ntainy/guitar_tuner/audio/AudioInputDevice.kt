package dev.ntainy.guitar_tuner.audio

enum class InputKind { BUILTIN_MIC, USB, WIRED_HEADSET, BLUETOOTH, OTHER, TEST_TONE }

/**
 * An input the tuner can listen to.
 *
 * @property id `AudioDeviceInfo.id` (changes between sessions); -1 for synthetic sources
 * @property key stable identity used in settings, e.g. "usb:NUX MP-3" or "builtin"
 * @property name what the user sees, e.g. "NUX Mighty Plug Pro (USB)"
 */
data class AudioInputDevice(
    val id: Int,
    val key: String,
    val name: String,
    val kind: InputKind,
) {
    val isUsb: Boolean get() = kind == InputKind.USB

    companion object {
        const val BUILTIN_KEY = "builtin"
        const val TEST_TONE_KEY = "test-tone"
    }
}
