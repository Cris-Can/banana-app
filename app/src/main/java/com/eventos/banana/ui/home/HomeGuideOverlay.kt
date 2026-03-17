package com.eventos.banana.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeGuideOverlay(
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }

    // Textos de cada paso
    val titles = listOf(
        "¡Bienvenido a Banana! 🍌",
        "Tu Perfil 👤",
        "Acciones Rápidas ⚡",
        "Filtros de Búsqueda 🔎",
        "¡Crea tu Evento! ➕"
    )
    val descriptions = listOf(
        "Aquí encontrarás los mejores eventos cerca de ti. Explora, únete y diviértete.",
        "Toca tu avatar arriba a la izquierda para ver tu reputación, eventos y configuración.",
        "Arriba a la derecha: 🔍 Buscar usuarios, 👥 Amigos, 🔔 Alertas y ✉️ Chat.",
        "Filtra por deporte, fiesta, cultura... Usa los chips para encontrar tu panorama.",
        "Toca el botón + abajo a la derecha para organizar un partido, junta o fiesta."
    )

    val total = titles.size
    val isLast = currentStep == total - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()  // ✅ La card nunca queda bajo los botones del sistema
    ) {

        // ─── Indicadores flotantes (flechas animadas sin fondo oscuro) ─────────
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "guide_step_arrow"
        ) { step ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (step) {
                    1 -> {
                        // 👤 Tu Perfil → arriba izquierda
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 52.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PulsingArrow(up = true)
                            GuideChip("Tu Perfil")
                        }
                    }
                    2 -> {
                        // ⚡ Acciones → arriba derecha
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 12.dp, top = 52.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PulsingArrow(up = true)
                            GuideChip("Acciones")
                        }
                    }
                    3 -> {
                        // 🔎 Filtros → centro superior (debajo de la topbar)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 112.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PulsingArrow(up = true)
                            GuideChip("Filtros")
                        }
                    }
                    4 -> {
                        // ➕ Crear evento → abajo derecha (FAB)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 150.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GuideChip("Crear")
                            PulsingArrow(up = false)
                        }
                    }
                    else -> { /* Paso 0 — bienvenida, sin flecha */ }
                }
            }
        }

        // ─── Card de instrucciones (parte inferior) ───────────────────────────
        ElevatedCard(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Título + paginación
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titles[currentStep],
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentStep + 1}/$total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }

                // Descripción
                Text(
                    text = descriptions[currentStep],
                    style = MaterialTheme.typography.bodyMedium
                )

                // Barra de progreso
                LinearProgressIndicator(
                    progress = { (currentStep + 1).toFloat() / total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("No volver a mostrar")
                    }
                    Button(
                        onClick = { if (isLast) onDismiss() else currentStep++ }
                    ) {
                        Text(if (isLast) "¡Listo! 🎉" else "Siguiente")
                    }
                }
            }
        }
    } // Cierre del Box exterior con navigationBarsPadding
}

// ─── Flecha pulsante animada ────────────────────────────────────────────────

@Composable
private fun PulsingArrow(up: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (up) -6f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    Text(
        text = if (up) "⬆️" else "⬇️",
        fontSize = 28.sp,
        modifier = Modifier.offset(y = offsetY.dp)
    )
}

// ─── Chip de etiqueta ────────────────────────────────────────────────────────

@Composable
private fun GuideChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
