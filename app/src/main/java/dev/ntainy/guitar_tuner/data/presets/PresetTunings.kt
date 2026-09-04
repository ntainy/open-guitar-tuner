package dev.ntainy.guitar_tuner.data.presets

import dev.ntainy.guitar_tuner.data.model.PresetIds
import dev.ntainy.guitar_tuner.data.model.Tuning
import dev.ntainy.guitar_tuner.data.model.TuningGroup

/**
 * The built-in tuning catalog, in display order: Standard, then Power, Transposed, Open and Extras.
 *
 * Presets never touch the disk. [dev.ntainy.guitar_tuner.data.DataStoreTuningsRepository] appends this list after the
 * user's own tunings on every read, so a change made here in code reaches the device with the next install.
 * Strings are MIDI numbers, low string first. The UI derives note chips from the numbers, so [Tuning.subtitle] is a
 * one-line hint about the tuning's character rather than a note list.
 */
object PresetTunings {
    /**
     * Every preset, in catalog order: the chromatic entry, then Standard, then Power, Transposed, Open and Extras.
     *
     * Chromatic sits in the list because that is where the user chooses what they are tuning; its strings are
     * Standard's and are never read (see [Tuning.isChromatic]). It is carried here rather than special-cased in the
     * repository so that selecting it, persisting it and resolving it back all work with no extra machinery.
     */
    val all: List<Tuning> = listOf(
        // Standard
        preset(
            PresetIds.CHROMATIC,
            "Chromatic",
            "Any note, no strings",
            TuningGroup.STANDARD,
            40, 45, 50, 55, 59, 64,
        ),
        preset(PresetIds.STANDARD, "Standard", "The everyday tuning", TuningGroup.STANDARD, 40, 45, 50, 55, 59, 64),

        // Power
        preset(id("drop_d"), "Drop D", "Low E down a tone", TuningGroup.POWER, 38, 45, 50, 55, 59, 64),
        preset(id("double_drop_d"), "Double Drop D", "Both E strings down a tone", TuningGroup.POWER, 38, 45, 50, 55, 59, 62),
        preset(id("dadgad"), "D modal (DADGAD)", "Celtic modal, drone friendly", TuningGroup.POWER, 38, 45, 50, 55, 57, 62),
        preset(id("daddad"), "Double Daddy (DADDAD)", "DADGAD with the G down to D", TuningGroup.POWER, 38, 45, 50, 50, 57, 62),
        preset(id("drop_c_sharp"), "Drop C♯", "Drop D, everything a half step down", TuningGroup.POWER, 37, 44, 49, 54, 58, 63),
        preset(id("drop_c"), "Drop C", "Drop D, everything a whole step down", TuningGroup.POWER, 36, 43, 48, 53, 57, 62),
        preset(id("drop_b"), "Drop B", "Drop D, everything down a minor third", TuningGroup.POWER, 35, 42, 47, 52, 56, 61),

        // Transposed
        preset(id("half_step_down"), "Half step down (E♭ standard)", "Everything down a semitone", TuningGroup.TRANSPOSED, 39, 44, 49, 54, 58, 63),
        preset(id("whole_step_down"), "Whole step down (D standard)", "Everything down a tone", TuningGroup.TRANSPOSED, 38, 43, 48, 53, 57, 62),
        preset(id("half_step_up"), "Half step up (F standard)", "Everything up a semitone", TuningGroup.TRANSPOSED, 41, 46, 51, 56, 60, 65),
        preset(id("whole_step_up"), "Whole step up (F♯ standard)", "Everything up a tone", TuningGroup.TRANSPOSED, 42, 47, 52, 57, 61, 66),
        preset(id("c_standard"), "C standard", "Everything down two tones", TuningGroup.TRANSPOSED, 36, 41, 46, 51, 55, 60),
        preset(id("b_standard"), "B standard", "Everything down a fourth", TuningGroup.TRANSPOSED, 35, 40, 45, 50, 54, 59),

        // Open
        preset(id("open_g"), "Open G", "Strums a G major chord", TuningGroup.OPEN, 38, 43, 50, 55, 59, 62),
        preset(id("open_d"), "Open D", "Strums a D major chord", TuningGroup.OPEN, 38, 45, 50, 54, 57, 62),
        preset(id("open_dm"), "Open Dm", "Strums a D minor chord", TuningGroup.OPEN, 38, 45, 50, 53, 57, 62),
        preset(id("open_e"), "Open E", "Strums an E major chord", TuningGroup.OPEN, 40, 47, 52, 56, 59, 64),
        preset(id("open_a"), "Open A", "Strums an A major chord", TuningGroup.OPEN, 40, 45, 52, 57, 61, 64),
        preset(id("open_c"), "Open C", "Strums a C major chord", TuningGroup.OPEN, 36, 43, 48, 55, 60, 64),

        // Extras
        preset(id("g_modal"), "G modal (DGDGCD)", "Open G with a suspended fourth", TuningGroup.EXTRAS, 38, 43, 50, 55, 60, 62),
        preset(id("all_fourths"), "All fourths (EADGCF)", "Every string a fourth apart", TuningGroup.EXTRAS, 40, 45, 50, 55, 60, 65),
        preset(id("nst"), "NST (CGDAEG)", "Fripp's New Standard Tuning, in fifths", TuningGroup.EXTRAS, 36, 43, 50, 57, 64, 67),
    )

    private val index: Map<String, Tuning> = all.associateBy { it.id }

    /** The preset with the given id, or null when [id] is not a preset. */
    fun byId(id: String): Tuning? = index[id]

    private fun id(slug: String): String = PresetIds.PREFIX + slug

    private fun preset(
        id: String,
        name: String,
        subtitle: String,
        group: TuningGroup,
        vararg strings: Int,
    ): Tuning = Tuning(
        id = id,
        name = name,
        subtitle = subtitle,
        strings = strings.toList(),
        isPreset = true,
        group = group,
    )
}
