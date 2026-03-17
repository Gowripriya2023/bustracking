package com.example.collegebustracking.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SlateBlueLight,
    onPrimary = Color.White,
    secondary = SlateBlueLight80,
    onSecondary = Color.White,
    tertiary = SlateBlueLight,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF2D2D30),
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = SlateBlue,
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF3D3553),
    onSecondaryContainer = Color.White,
    surfaceVariant = Color(0xFF3D3553),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val LightColorScheme = lightColorScheme(
    primary = SlateBlue,
    onPrimary = Color.White,
    secondary = SlateBlueLight,
    onSecondary = Color.White,
    tertiary = SlateBlue,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = SlateBlueLight80,
    onPrimaryContainer = SlateBlue,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = SlateBlue,
    surfaceVariant = Color(0xFFF0ECF8),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun CollegeBusTrackingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}