package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CeruleanPrimary,
    onPrimary = CeruleanOnPrimary,
    primaryContainer = CeruleanContainer,
    onPrimaryContainer = CeruleanOnContainer,
    secondary = AmberSecondary,
    onSecondary = AmberOnSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = SageTertiary,
    onTertiary = SageOnTertiary,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = SageOnContainer,
    background = AlabasterBackground,
    onBackground = InkPrimary,
    surface = CrispPaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = MineralSurfaceVariant,
    onSurfaceVariant = GraphiteSecondary,
    surfaceContainerLowest = CrispPaperSurface,
    surfaceContainerLow = AlabasterBackground,
    surfaceContainer = MineralSurfaceVariant,
    surfaceContainerHigh = MineralContainerHigh,
    outline = MineralOutline,
    outlineVariant = MineralOutlineSubtle,
    error = CrimsonError,
    onError = CeruleanOnPrimary,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonOnContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = GoldOnSecondaryDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = GoldOnContainerDark,
    tertiary = MintTertiaryDark,
    onTertiary = MintOnTertiaryDark,
    tertiaryContainer = MintContainerDark,
    onTertiaryContainer = MintOnContainerDark,
    background = ObsidianBackground,
    onBackground = OffWhiteInk,
    surface = SlateSurface,
    onSurface = OffWhiteInk,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SilverGraphite,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceVariant,
    surfaceContainerHigh = SlateContainerHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutlineSubtle,
    error = RoseErrorDark,
    onError = ObsidianBackground,
    errorContainer = RoseContainerDark,
    onErrorContainer = RoseOnContainerDark,
)

val CandorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CandorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CandorTypography,
        shapes = CandorShapes,
        content = content,
    )
}
