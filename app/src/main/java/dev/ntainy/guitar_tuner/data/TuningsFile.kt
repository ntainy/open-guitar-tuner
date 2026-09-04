package dev.ntainy.guitar_tuner.data

import dev.ntainy.guitar_tuner.data.model.Tuning
import kotlinx.serialization.Serializable

/**
 * On-disk shape of `tunings.json`: the user's custom tunings only.
 *
 * Presets are never written; they come from [dev.ntainy.guitar_tuner.data.presets.PresetTunings] on every read so
 * catalog updates shipped in code always win. [version] is stamped on every write so a future build can migrate an
 * older document instead of guessing its shape.
 */
@Serializable
data class TuningsFile(
    val version: Int = CURRENT_VERSION,
    val tunings: List<Tuning> = emptyList(),
) {
    companion object {
        /** Document version written by this build. Bump when the shape of [Tuning] or this file changes. */
        const val CURRENT_VERSION = 1
    }
}
