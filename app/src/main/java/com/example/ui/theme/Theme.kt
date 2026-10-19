package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = GoogleBlueDark,
    onSecondary = Color.White,
    background = GoogleSurfaceLight,
    onBackground = GoogleTextPrimary,
    surface = GoogleCardBg,
    onSurface = GoogleTextPrimary,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = GoogleTextSecondary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
