package com.eventos.banana.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class GuideStep(
    val emoji: String,
    val title: String,
    val description: String
)

private val steps = listOf(
    GuideStep("👤", "Tu Perfil",
        "⬆️ Arriba a la izquierda — tu foto y nombre.\nToca para editar perfil, ver tu reputación y ajustes."),
    GuideStep("🗺️", "Mapa / Lista",
        "⬆️ Arriba a la derecha — el primer ícono cambia entre\nel mapa interactivo y la lista de eventos."),
    GuideStep("🏷️", "Categorías",
        "⬆️ Debajo de la barra superior — filtra eventos por tipo:\nFiesta, Deporte, Cultura, Comida y más."),
    GuideStep("📍", "Radio de Búsqueda",
        "📍 En el centro de la pantalla — ajusta el radio\npara ver eventos cerca de tu ubicación."),
    GuideStep("🎯", "Centrar Mapa",
        "⬇️ Abajo a la izquierda (sobre el botón +) —\nte devuelve a tu ubicación actual en el mapa."),
    GuideStep("➕", "Crear Evento",
        "⬇️ Abajo a la derecha — el botón + te permite\ncrear tu propio evento y compartirlo."),
    GuideStep("👥", "Amigos",
        "⬆️ Arriba a la derecha (tercer ícono) —\ntoca para ver tus amigos y conectar con personas."),
    GuideStep("🔍", "Búsqueda",
        "⬆️ Arriba a la derecha (segundo ícono) — la lupa\nbusca eventos por título y usuarios por nickname.")
)

@Composable
fun HomeGuideOverlay(onDismiss: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }
    var overlayAlpha by remember { mutableStateOf(0f) }
    val animatedAlpha by animateFloatAsState(targetValue = overlayAlpha, label = "fade")

    LaunchedEffect(Unit) { overlayAlpha = 0.85f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(animatedAlpha)
            .background(Color.Black)
            .clickable { /* Bloquea toques al contenido subyacente */ }
    ) {
        val step = steps[currentStep]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(step.emoji, fontSize = 40.sp)
                    Text(
                        step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            if (currentStep < steps.size - 1) "Siguiente  →" else "¡Entendido!",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Saltar tutorial", color = Color.Gray)
                    }

                    Text(
                        "Paso ${currentStep + 1} / ${steps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
