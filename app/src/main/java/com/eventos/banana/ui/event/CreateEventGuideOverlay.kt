package com.eventos.banana.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CreateEventGuideOverlay(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() }
    ) {
        // 1. Basic Info (Top)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 16.dp)
        ) {
            Text("📝 Info Básica", 
                color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                "Define un título atractivo\ny una descripción clara.",
                color = Color.LightGray, style = MaterialTheme.typography.bodyMedium
            )
        }

        // 2. Type & Location (Center)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Text("📍 Ubicación y Tipo", 
                color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                "Elige la categoría correcta\npara que te encuentren fácil.",
                color = Color.LightGray, style = MaterialTheme.typography.bodyMedium
            )
        }

        // 3. Create Button (Bottom End)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("⬇️", style = MaterialTheme.typography.displayMedium)
            Text("Publicar", 
                color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                "Cuando estés listo,\n¡lánzalo al mundo!",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        
        // Dismiss Hint
        Text(
            "Toca para cerrar guía",
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
