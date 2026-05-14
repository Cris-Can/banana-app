package com.eventos.banana.ui.ranking

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

@Composable
fun RankingGuideOverlay(onDismiss: () -> Unit) {
    var alpha by remember { mutableStateOf(0f) }
    val animatedAlpha by animateFloatAsState(targetValue = alpha, label = "fade")

    LaunchedEffect(Unit) { alpha = 0.85f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(animatedAlpha)
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🏆", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Ranking de Usuarios",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    "• Los usuarios aparecen ordenados por puntuación\n" +
                    "• Asiste a eventos y recibe calificaciones para subir\n" +
                    "• Sé un Top Banana y destaca en tu comuna\n" +
                    "• Tu reputación abre más oportunidades",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Text("Entendido", color = Color.White)
                }
            }
        }
    }
}
