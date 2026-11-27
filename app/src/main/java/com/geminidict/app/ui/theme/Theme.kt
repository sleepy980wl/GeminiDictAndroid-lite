package com.geminidict.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Modern Apple iOS / macOS inspired color palette
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),          // iOS System Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF003D80),
    secondary = Color(0xFF5856D6),        // iOS System Indigo
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEEFC),
    onSecondaryContainer = Color(0xFF28257A),
    tertiary = Color(0xFF34C759),         // iOS System Green
    background = Color(0xFFF2F2F7),       // iOS Grouped Background
    surface = Color.White,
    surfaceVariant = Color(0xFFE5E5EA),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF636366),
    error = Color(0xFFFF3B30),
    errorContainer = Color(0xFFFFE5E4),
    onErrorContainer = Color(0xFF8C1D18)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),          // iOS Dark System Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2A4A),
    onPrimaryContainer = Color(0xFFB3D7FF),
    secondary = Color(0xFF5E5CE6),        // iOS Dark System Indigo
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF242352),
    onSecondaryContainer = Color(0xFFD6D5FF),
    tertiary = Color(0xFF30D158),         // iOS Dark System Green
    background = Color(0xFF000000),       // True OLED Black
    surface = Color(0xFF1C1C1E),          // iOS Elevated Dark Card
    surfaceVariant = Color(0xFF2C2C2E),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2C2C2E),
    onBackground = Color(0xFFF2F2F7),
    onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFFAEAEB2),
    error = Color(0xFFFF453A),
    errorContainer = Color(0xFF4A1917),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val ModernShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GeminiDictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ModernShapes,
        content = content
    )
}
