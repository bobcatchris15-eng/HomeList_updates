package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HomeListLightColors = lightColorScheme(
    primary = TerracottaClay,
    onPrimary = Color.White,
    primaryContainer = SoftClayBlush,
    onPrimaryContainer = OnClayBlush,
    secondary = BotanicalSage,
    onSecondary = Color.White,
    secondaryContainer = SoftSageMist,
    onSecondaryContainer = OnSageMist,
    tertiary = HoneyAmber,
    onTertiary = Color.White,
    tertiaryContainer = SoftAmberGlow,
    onTertiaryContainer = OnAmberGlow,
    background = LinenCream,
    onBackground = DeepEspresso,
    surface = ElevatedIvory,
    onSurface = DeepEspresso,
    surfaceVariant = WarmParchment,
    onSurfaceVariant = WarmStoneInk,
    surfaceContainerLowest = ElevatedIvory,
    surfaceContainerLow = WarmParchment,
    surfaceContainer = WarmSandContainer,
    surfaceContainerHigh = Color(0xFFE7DAC9),
    outline = WarmStoneInk,
    outlineVariant = SoftStoneBorder,
    error = WarmError,
    onError = Color.White,
    errorContainer = WarmErrorContainer,
    onErrorContainer = Color(0xFF5D1812)
)

private val HomeListDarkColors = darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = Color(0xFF3E180C),
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = DarkSage,
    onSecondary = Color(0xFF142A1B),
    secondaryContainer = DarkSageContainer,
    onSecondaryContainer = Color(0xFFD0E7D5),
    tertiary = DarkAmber,
    onTertiary = Color(0xFF3A2304),
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = Color(0xFFFFE1B3),
    background = HearthSlateBg,
    onBackground = CreamInk,
    surface = HearthSlateSurface,
    onSurface = CreamInk,
    surfaceVariant = HearthSlateCard,
    onSurfaceVariant = MutedCreamInk,
    surfaceContainerLowest = HearthSlateBg,
    surfaceContainerLow = HearthSlateSurface,
    surfaceContainer = HearthSlateCard,
    surfaceContainerHigh = Color(0xFF37302B),
    outline = MutedCreamInk,
    outlineVariant = HearthSlateBorder,
    error = Color(0xFFEC8A80),
    onError = Color(0xFF4F130D),
    errorContainer = Color(0xFF6B221B),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val HomeListShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HomeListTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HomeListDarkColors else HomeListLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HomeListTypography,
        shapes = HomeListShapes,
        content = content
    )
}
