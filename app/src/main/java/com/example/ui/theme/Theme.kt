package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DakkYellow,
    onPrimary = Color.Black,
    primaryContainer = DakkOrange,
    onPrimaryContainer = Color.White,
    secondary = DakkOrange,
    onSecondary = Color.White,
    secondaryContainer = DakkDarkSurfaceVariant,
    onSecondaryContainer = DakkYellow,
    tertiary = DakkYellowLight,
    background = DakkDarkBg,
    onBackground = DakkTextPrimaryDark,
    surface = DakkDarkSurface,
    onSurface = DakkTextPrimaryDark,
    surfaceVariant = DakkDarkSurfaceVariant,
    onSurfaceVariant = DakkTextSecondaryDark,
    outline = DakkDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = DakkYellowDark,
    onPrimary = Color.Black,
    primaryContainer = DakkYellowLight,
    onPrimaryContainer = Color.Black,
    secondary = DakkOrange,
    onSecondary = Color.White,
    background = DakkLightBg,
    onBackground = DakkTextPrimaryLight,
    surface = DakkLightSurface,
    onSurface = DakkTextPrimaryLight,
    surfaceVariant = DakkLightSurfaceVariant,
    onSurfaceVariant = DakkTextSecondaryLight,
    outline = DakkLightBorder
)

@Composable
fun DakkTheme(
    darkTheme: Boolean = true, // Defaults to OLED dark as preferred by user
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = DakkTheme(darkTheme = darkTheme, content = content)
