package com.example.talabatmart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A1F2B),            // maroon
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DDE0),
    onPrimaryContainer = Color(0xFF3B0A12),
    secondary = Color(0xFFC8962E),          // gold accent
    onSecondary = Color.White,
    background = Color(0xFFFFF8F3),         // warm cream
    onBackground = Color(0xFF2B1B1D),
    surface = Color.White,
    onSurface = Color(0xFF2B1B1D),
    outline = Color(0xFFB9A3A6),
    error = Color(0xFFB3261E)
)

@Composable
fun TalabatMartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}