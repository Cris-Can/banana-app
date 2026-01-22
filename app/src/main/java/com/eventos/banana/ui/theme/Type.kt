package com.eventos.banana.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    // Títulos Principales (Modern Bold)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp
    ),
    displayMedium = TextStyle( // Used for big headers
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold, // "Poppins Bold" equivalent
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    titleLarge = TextStyle( // Screen titles
        fontFamily = FontFamily.Default, // "Montserrat Bold" equivalent
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle( // Section headers
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    
    // Cuerpo (Readable)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, // "Inter/Roboto Regular"
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Botones / Acentos (SemiBold)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, // "Poppins SemiBold"
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)