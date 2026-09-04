package dev.ntainy.guitar_tuner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.ntainy.guitar_tuner.data.model.ThemeMode
import dev.ntainy.guitar_tuner.ui.theme.TunerColors as C

val DarkScheme = darkColorScheme(
    primary = C.Brass,
    onPrimary = C.OnBrass,
    primaryContainer = C.BrassContainer,
    onPrimaryContainer = C.Brass,
    inversePrimary = C.BrassDeep,
    secondary = C.Steel,
    onSecondary = C.Graphite900,
    secondaryContainer = C.Graphite600,
    onSecondaryContainer = C.Ink,
    tertiary = C.Mint,
    onTertiary = C.Graphite950,
    tertiaryContainer = C.MintContainer,
    onTertiaryContainer = C.Mint,
    error = C.Coral,
    onError = C.Graphite950,
    errorContainer = C.CoralContainer,
    onErrorContainer = C.Coral,
    background = C.Graphite900,
    onBackground = C.Ink,
    surface = C.Graphite900,
    onSurface = C.Ink,
    surfaceVariant = C.Graphite700,
    onSurfaceVariant = C.Muted,
    surfaceTint = C.Brass,
    inverseSurface = C.Ink,
    inverseOnSurface = C.Graphite900,
    outline = C.OutlineDark,
    outlineVariant = C.OutlineVariantDark,
    scrim = Color.Black,
    surfaceBright = C.Graphite600,
    surfaceDim = C.Graphite950,
    surfaceContainerLowest = C.Graphite950,
    surfaceContainerLow = C.Graphite850,
    surfaceContainer = C.Graphite800,
    surfaceContainerHigh = C.Graphite700,
    surfaceContainerHighest = C.Graphite600,
)

val LightScheme = lightColorScheme(
    primary = C.BrassDeep,
    onPrimary = Color.White,
    primaryContainer = C.BrassContainerLight,
    onPrimaryContainer = C.OnBrass,
    inversePrimary = C.Brass,
    secondary = C.SteelDeep,
    onSecondary = Color.White,
    secondaryContainer = C.PaperContainer,
    onSecondaryContainer = C.InkLight,
    tertiary = C.MintDeep,
    onTertiary = Color.White,
    tertiaryContainer = C.MintContainerLight,
    onTertiaryContainer = C.MintDeep,
    error = C.CoralDeep,
    onError = Color.White,
    errorContainer = C.CoralContainerLight,
    onErrorContainer = C.CoralDeep,
    background = C.Paper,
    onBackground = C.InkLight,
    surface = C.Paper,
    onSurface = C.InkLight,
    surfaceVariant = C.PaperContainer,
    onSurfaceVariant = C.MutedLight,
    surfaceTint = C.BrassDeep,
    inverseSurface = C.InkLight,
    inverseOnSurface = C.Paper,
    outline = C.OutlineLight,
    outlineVariant = C.OutlineVariantLight,
    scrim = Color.Black,
    surfaceBright = C.PaperSurface,
    surfaceDim = C.PaperContainerHigh,
    surfaceContainerLowest = C.PaperSurface,
    surfaceContainerLow = C.PaperLow,
    surfaceContainer = C.PaperContainer,
    surfaceContainerHigh = C.PaperContainerHigh,
    surfaceContainerHighest = C.PaperContainerHighest,
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun GuitarTunerTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = TunerTypography,
        content = content,
    )
}
