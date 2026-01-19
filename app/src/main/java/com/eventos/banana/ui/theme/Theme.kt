package com.eventos.banana.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext


// 🎨 ESQUEMAS DE COLOR

// 1. BANANA (Dark Yellow/Black) - Default & Identidad
private val BananaScheme = darkColorScheme(
    primary = BananaYellow,
    onPrimary = Color.Black,
    primaryContainer = BananaYellowDark,
    onPrimaryContainer = Color.Black,
    secondary = BananaLeaf,
    onSecondary = Color.White,
    tertiary = BananaCream,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed
)

// 2. DARK (Standard Material Dark - Grey/Purple)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), // Purple80
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF25232A), // Slightly lighter than background
    onBackground = Color.White,
    onSurface = Color.White
)

// 3. LIGHT (Clean White/Yellow Accents)
private val LightScheme = lightColorScheme(
    primary = BananaYellowDark, // Readable on white
    onPrimary = Color.Black,
    secondary = BananaLeaf,
    tertiary = BananaYellow,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun BananaTheme(
    // 🎨 Theme Mode: "BANANA", "DARK", "LIGHT"
    themeMode: String = "BANANA",
    dynamicColor: Boolean = false, // Disabled for consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "LIGHT" -> LightScheme
        "DARK" -> DarkScheme
        else -> BananaScheme // "BANANA" is default
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}