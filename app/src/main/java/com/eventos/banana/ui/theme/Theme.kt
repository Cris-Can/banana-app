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
// 1. BANANA (Harmonic Dark)
private val BananaScheme = darkColorScheme(
    primary = BananaGold, // Premium Gold
    onPrimary = Color.Black,
    primaryContainer = BananaGoldDim, 
    onPrimaryContainer = Color.White,
    secondary = BananaGold, // Unified Gold
    onSecondary = Color.Black,
    tertiary = BananaGold, // Minimalist Palette
    background = BananaDarkBackground,
    surface = BananaDarkSurface,
    onBackground = BananaWhite,
    onSurface = BananaWhite,
    surfaceVariant = BananaDarkSurfaceVariant,
    onSurfaceVariant = BananaGrey,
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
    // 🎨 Theme Mode: "BANANA", "DARK", "LIGHT", "DYNAMIC"
    themeMode: String = "BANANA",
    dynamicColor: Boolean = false, // Set to true to enable Material You
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    
    // Determine effective dynamic flag
    val useDynamic = (dynamicColor || themeMode == "DYNAMIC") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic -> {
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == "LIGHT" -> LightScheme
        themeMode == "DARK" -> DarkScheme
        else -> BananaScheme // "BANANA" is default
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}