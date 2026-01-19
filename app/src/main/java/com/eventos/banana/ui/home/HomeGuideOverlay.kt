package com.eventos.banana.ui.home

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
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HomeGuideOverlay(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() } // Dimiss on tap anywhere
    ) {
        // 1. Profile (Top Left)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 80.dp) // Adjust for status bar/top bar
        ) {
            Text("⬆️", style = MaterialTheme.typography.displayMedium)
            Text(
                "Tu Perfil",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Toca tu foto para ver\ntus datos y reputación.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 2. Chat & Notifs (Top Right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 80.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("⬆️", style = MaterialTheme.typography.displayMedium)
            Text(
                "Mensajes y Alertas",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.End
            )
            Text(
                "Chatea con amigos y\nrevisa notificaciones.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }

        // 3. Filters (Upper Center)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 180.dp), // Below TopBar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Busca Eventos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Filtra por Región y Comuna\npara ver qué pasa cerca de ti.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text("⬇️", style = MaterialTheme.typography.displayMedium)
        }

        // 4. Create Event (Bottom Right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 100.dp), // Above FAB
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "¡Crea tu Evento!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Toca el + para organizar\nun partido, junta o fiesta.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
            Text("⬇️", style = MaterialTheme.typography.displayMedium)
        }

        // Dismiss Hint
        Text(
            "Toca cualquier parte para cerrar",
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
