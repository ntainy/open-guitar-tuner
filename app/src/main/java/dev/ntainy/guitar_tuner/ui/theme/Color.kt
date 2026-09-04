package dev.ntainy.guitar_tuner.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** "Graphite & brass": graphite-blue grounds, brass for the thing you turn, mint = in tune, coral = far off. */
object TunerColors {
    val Graphite950 = Color(0xFF0A0D12)
    val Graphite900 = Color(0xFF0E1117)
    val Graphite850 = Color(0xFF12161E)
    val Graphite800 = Color(0xFF161B24)
    val Graphite700 = Color(0xFF1E2431)
    val Graphite600 = Color(0xFF262D3B)
    val Graphite500 = Color(0xFF2E3646)
    val OutlineDark = Color(0xFF3A4252)
    val OutlineVariantDark = Color(0xFF29303D)

    val Brass = Color(0xFFE2B65A)
    val OnBrass = Color(0xFF241A05)
    val BrassContainer = Color(0xFF3A2E12)
    val BrassDeep = Color(0xFF9C7118)
    val BrassContainerLight = Color(0xFFF1E5C6)

    val Steel = Color(0xFF9AA6BD)
    val SteelDeep = Color(0xFF5B6270)
    val Mint = Color(0xFF3DD68C)
    val MintDeep = Color(0xFF1B8F60)
    val MintContainer = Color(0xFF143224)
    val MintContainerLight = Color(0xFFD9F2E6)
    val Coral = Color(0xFFF07A64)
    val CoralDeep = Color(0xFFC9503A)
    val CoralContainer = Color(0xFF3A1F1A)
    val CoralContainerLight = Color(0xFFF9DED8)

    val Ink = Color(0xFFE9ECF2)
    val Muted = Color(0xFF98A1B3)

    val Paper = Color(0xFFF3F4F7)
    val PaperSurface = Color(0xFFFFFFFF)
    val PaperLow = Color(0xFFF8F9FB)
    val PaperContainer = Color(0xFFE9EBF1)
    val PaperContainerHigh = Color(0xFFDFE2EA)
    val PaperContainerHighest = Color(0xFFD5D9E3)
    val InkLight = Color(0xFF161A23)
    val MutedLight = Color(0xFF5D6474)
    val OutlineLight = Color(0xFFB8BECB)
    val OutlineVariantLight = Color(0xFFD8DBE3)
}

/** Semantic aliases so screens never hard-code a hue. */
val ColorScheme.inTune: Color get() = tertiary
val ColorScheme.inTuneContainer: Color get() = tertiaryContainer
val ColorScheme.farOff: Color get() = error
val ColorScheme.target: Color get() = primary
