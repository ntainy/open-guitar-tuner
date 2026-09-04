package dev.ntainy.guitar_tuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.ntainy.guitar_tuner.R

/** Bricolage Grotesque (OFL, bundled variable font) for display text and the note glyph. Body stays Roboto. */
val Bricolage: FontFamily = FontFamily(
    Font(
        R.font.bricolage_grotesque_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500), FontVariation.Setting("opsz", 24f)),
    ),
    Font(
        R.font.bricolage_grotesque_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("opsz", 48f)),
    ),
    Font(
        R.font.bricolage_grotesque_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("opsz", 96f)),
    ),
)

val TunerTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Medium),
    )
}
