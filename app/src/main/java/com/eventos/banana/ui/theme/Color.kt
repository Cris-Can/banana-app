package com.eventos.banana.ui.theme

import androidx.compose.ui.graphics.Color

// 🎨 BANANA BRAND COLORS (Harmonic Update)

// Primary Brand
val BananaYellow = Color(0xFFFFD600)    // Vivid Yellow (Material 700)
val BananaYellowLight = Color(0xFFFFEA00) // Lighter accent

// Backgrounds (Clean Dark Mode)
val BananaDarkBackground = Color(0xFF121212)
val BananaDarkSurface = Color(0xFF1E1E1E)
val BananaDarkSurfaceVariant = Color(0xFF2D2D2D)

// Text
val BananaWhite = Color(0xFFF5F5F5)    // High Emphasis
val BananaGrey = Color(0xFFB0B0B0)     // Medium Emphasis (Subtitles)

// Legacy / Accents
val BananaOrange = Color(0xFFFFAB00)   // Secondary Accent
val ErrorRed = Color(0xFFCF6679)
val SuccessGreen = Color(0xFF4CAF50)

// Legacy Support (Restore missing colors for LightScheme/Old configs)
val BananaCharcoal = Color(0xFF2C3E50)
val BananaLightBackground = Color(0xFFFFFFFF)
val BananaBlack = Color(0xFF1A1A1A)
val BananaCream = Color(0xFFFFF9C4) 
val BananaLeaf = Color(0xFF558B2F)
val VerifiedBlue = Color(0xFF1DA1F2)

// Standard Dark Mode (OLED/Material) - Now aligned with BananaDark
val StandardDarkBackground = BananaDarkBackground
val StandardDarkSurface = BananaDarkSurface
val StandardPrimary = BananaYellow
// val StandardPrimary = Color(0xFFD0BCFF) // Purple80 equivalent or Yellow