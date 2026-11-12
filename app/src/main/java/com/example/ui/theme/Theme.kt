package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.local.AppThemeMode

// Strict Black & White Dark Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = AstraWhite,
    onPrimary = AstraBlack,
    primaryContainer = AstraDarkCard,
    onPrimaryContainer = AstraWhite,
    secondary = AstraWhite,
    onSecondary = AstraBlack,
    secondaryContainer = AstraDarkCard,
    onSecondaryContainer = AstraWhite,
    tertiary = AstraDarkSecondaryText,
    onTertiary = AstraBlack,
    background = AstraBlack,
    onBackground = AstraWhite,
    surface = AstraDarkSurface,
    onSurface = AstraWhite,
    surfaceVariant = AstraDarkCard,
    onSurfaceVariant = AstraDarkSecondaryText,
    outline = AstraDarkBorder,
    outlineVariant = AstraDarkBorder
)

// Strict Black & White Light Color Scheme
private val LightColorScheme = lightColorScheme(
    primary = AstraBlack,
    onPrimary = AstraWhite,
    primaryContainer = AstraLightCard,
    onPrimaryContainer = AstraBlack,
    secondary = AstraBlack,
    onSecondary = AstraWhite,
    secondaryContainer = AstraLightCard,
    onSecondaryContainer = AstraBlack,
    tertiary = AstraLightSecondaryText,
    onTertiary = AstraWhite,
    background = AstraLightBackground,
    onBackground = AstraBlack,
    surface = AstraLightSurface,
    onSurface = AstraBlack,
    surfaceVariant = AstraLightCard,
    onSurfaceVariant = AstraLightSecondaryText,
    outline = AstraLightBorder,
    outlineVariant = AstraLightBorder
)

@Composable
fun AstraTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
