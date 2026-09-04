package dev.ntainy.guitar_tuner.ui.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The strength of one haptic, in the only four steps a phone actuator reliably distinguishes.
 *
 * Named by weight rather than by meaning: [TunerHaptics] owns the mapping from what happened to how it feels.
 */
enum class Feel { TICK, CLICK, DOUBLE, HEAVY }

/** What actually shakes the phone. Swapped for a silent stand-in in previews and tests. */
fun interface HapticOutput {
    fun play(feel: Feel)
}

/**
 * The tuner's haptic vocabulary, in one place so every screen speaks the same language, and so the
 * "Haptics" setting has a single gate to close. Call sites say what happened ([confirm], [step]); this decides
 * how it feels.
 *
 * This drives the vibrator directly rather than going through Compose's `performHapticFeedback`. That route is
 * gated by Android's system-wide "touch feedback" setting, which is commonly switched off to stop the keyboard
 * buzzing — and a tuner's haptics are not decorative touch feedback but the answer to "did that string land?",
 * which you want while looking at the guitar rather than the screen. The app's own Haptics switch is therefore
 * the authority; one tap in Settings silences the lot.
 */
@Stable
class TunerHaptics(private val output: HapticOutput, val enabled: Boolean) {

    /** One step of a slider or stepper. Frequent by design — the lightest thing in the vocabulary. */
    fun step() = perform(Feel.TICK)

    /** A switch, a segmented choice, AUTO on/off, pinning or unpinning a string. */
    fun toggle(on: Boolean) = perform(if (on) Feel.CLICK else Feel.TICK)

    /** A string just earned its tuned mark, or an action completed. */
    fun confirm() = perform(Feel.CLICK)

    /** The needle has just entered the in-tune band. Rate-limited by the caller. */
    fun enterBand() = perform(Feel.TICK)

    /** Every string of the tuning is in tune. The firmest thing the app does. */
    fun allTuned() = perform(Feel.DOUBLE)

    /** A long-press was recognised, e.g. the reference tone. */
    fun longPress() = perform(Feel.HEAVY)

    /** An action finished and the finger can leave. */
    fun gestureEnd() = perform(Feel.TICK)

    /** Something was refused: an invalid tuning, a control that cannot move further. */
    fun reject() = perform(Feel.DOUBLE)

    /**
     * Plays regardless of the setting. Only for the Haptics switch itself: when you turn it on, the gate is
     * still closed at that instant, and a switch that demonstrates nothing is a switch you cannot judge.
     */
    fun demo() = output.play(Feel.CLICK)

    private fun perform(feel: Feel) {
        if (enabled) output.play(feel)
    }
}

/** The real thing: predefined effects, which the platform maps to whatever the device's actuator does best. */
class SystemHapticOutput(context: Context) : HapticOutput {
    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }

    override fun play(feel: Feel) {
        val target = vibrator ?: return
        val effect = when (feel) {
            Feel.TICK -> VibrationEffect.EFFECT_TICK
            Feel.CLICK -> VibrationEffect.EFFECT_CLICK
            Feel.DOUBLE -> VibrationEffect.EFFECT_DOUBLE_CLICK
            Feel.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
        }
        runCatching { target.vibrate(VibrationEffect.createPredefined(effect)) }
    }
}

/** Silent stand-in so previews, tests and any composable outside the provider are safe to call. */
private val NoHaptics = HapticOutput { }

/**
 * Haptics for the current screen. Provided once in `MainActivity` from the user's setting; the default is
 * silent so a composable can always call it.
 */
val LocalTunerHaptics = staticCompositionLocalOf { TunerHaptics(NoHaptics, enabled = false) }

@Composable
fun rememberTunerHaptics(enabled: Boolean): TunerHaptics {
    val context = LocalContext.current
    val output = remember(context) { SystemHapticOutput(context.applicationContext) }
    return remember(output, enabled) { TunerHaptics(output, enabled) }
}
