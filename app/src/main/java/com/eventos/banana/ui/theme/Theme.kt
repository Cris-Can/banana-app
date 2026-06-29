package com.eventos.banana.ui.theme

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

// 1. BANANA (Original Theme)
private val BananaScheme = darkColorScheme(
    primary = BananaGold,
    onPrimary = Color.Black,
    primaryContainer = BananaGoldDim,
    onPrimaryContainer = Color.White,
    secondary = BananaGold,
    onSecondary = Color.Black,
    tertiary = BananaGold,
    background = BananaDarkBackground,
    surface = BananaDarkSurface,
    onBackground = BananaWhite,
    onSurface = BananaWhite,
    surfaceVariant = BananaDarkSurfaceVariant,
    onSurfaceVariant = BananaGrey,
    error = ErrorSoft
)

// 2. PANORAMAS (Premium Dark - Gold + Coral)
private val PanoramasDarkScheme = darkColorScheme(
    primary = PanoramasGold,
    onPrimary = Color.Black,
    primaryContainer = PanoramasGoldDim,
    onPrimaryContainer = Color.White,
    secondary = PanoramasGold,
    onSecondary = Color.Black,
    tertiary = PanoramasAccent,
    background = PanoramasBackground,
    surface = PanoramasSurface,
    onBackground = PanoramasOnBackground,
    onSurface = PanoramasOnBackground,
    surfaceVariant = PanoramasSurfaceVariant,
    onSurfaceVariant = PanoramasOnSurfaceVariant,
    error = ErrorSoft
)

// 3. DARK (Pure OLED)
private val DarkScheme = darkColorScheme(
    primary = DarkGold,
    onPrimary = Color.Black,
    primaryContainer = DarkGold,
    onPrimaryContainer = Color.Black,
    secondary = DarkGold,
    onSecondary = Color.Black,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = ErrorSoft
)

// 4. LIGHT (Clean white)
private val LightScheme = lightColorScheme(
    primary = BananaGold,
    onPrimary = Color.Black,
    primaryContainer = BananaGoldDim,
    onPrimaryContainer = Color.White,
    secondary = BananaGold,
    onSecondary = Color.Black,
    tertiary = LightTertiary,
    background = BananaLightBackground,
    surface = BananaLightBackground,
    onBackground = LightOnBackground,
    onSurface = LightOnBackground,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF666666),
    error = ErrorSoft
)

// 5. SPECTRUM (Sunset Neon)
private val SpectrumScheme = darkColorScheme(
    primary = SpectrumPrimary,
    onPrimary = Color.Black,
    primaryContainer = SpectrumPrimaryDim,
    onPrimaryContainer = Color.White,
    secondary = SpectrumSecondary,
    onSecondary = Color.Black,
    tertiary = SpectrumTertiary,
    background = SpectrumBackground,
    surface = SpectrumSurface,
    onBackground = SpectrumOnBackground,
    onSurface = SpectrumOnBackground,
    surfaceVariant = SpectrumSurfaceVariant,
    onSurfaceVariant = SpectrumOnSurfaceVariant,
    error = ErrorSoft
)

@Composable
fun PanoramasTheme(
    themeMode: String = "SPECTRUM",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDynamic = dynamicColor && (themeMode == "DYNAMIC") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic -> {
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == "SPECTRUM" -> SpectrumScheme
        themeMode == "PANORAMAS" -> PanoramasDarkScheme
        themeMode == "LIGHT" -> LightScheme
        themeMode == "DARK" -> DarkScheme
        else -> BananaScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PanoramasTypography,
        content = content
    )
}