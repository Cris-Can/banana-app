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

// 1. BANANA (Alto Impacto - Dark Slate/Yellow)
private val BananaScheme = darkColorScheme(
    primary = BananaYellow,
    onPrimary = BananaCharcoal, // Contrast on yellow
    primaryContainer = BananaYellow,
    onPrimaryContainer = BananaCharcoal,
    secondary = BananaOrange,   // For gradients/accents
    onSecondary = Color.Black,
    tertiary = BananaCream,
    background = BananaCharcoal,
    surface = BananaCharcoal,   // Match background for flat look or slightly lighter
    onBackground = BananaWhite,
    onSurface = BananaWhite,
    error = ErrorRed
)

// 2. DARK (Standard OLED Black)
private val DarkScheme = darkColorScheme(
    primary = BananaYellow,     // Keep brand accent
    onPrimary = Color.Black,
    secondary = BananaOrange,
    background = StandardDarkBackground, // #121212
    surface = StandardDarkSurface,       // #1E1E1E
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed
)

// 3. LIGHT (Fresco - White/Orange/Black)
private val LightScheme = lightColorScheme(
    primary = BananaOrange,
    onPrimary = Color.Black,
    secondary = BananaYellow,
    tertiary = BananaLeaf,
    background = BananaLightBackground,
    surface = BananaLightBackground,
    onBackground = BananaBlack,
    onSurface = BananaBlack
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