package com.eventos.banana.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.viewmodel.GuideStep
import com.eventos.banana.viewmodel.GuideViewModel

@Composable
fun GuideOverlay(
    viewModel: GuideViewModel
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isVisible by viewModel.isVisible.collectAsState()

    AnimatedVisibility(
        visible = isVisible && currentStep != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomCenter) // Position at bottom
            .padding(16.dp)
            .padding(bottom = 80.dp) // Avoid overlapping bottom nav if present
    ) {
        val step = currentStep ?: return@AnimatedVisibility

        val (title, description) = when (step) {
            GuideStep.HOME_WELCOME -> "¡Bienvenido a Banana! 🍌" to "Aquí encontrarás los mejores eventos cerca de ti. Explora, únete y diviértete."
            GuideStep.HOME_FILTERS -> "Encuentra lo tuyo 🔍" to "Usa los filtros superiores para ver solo eventos Deportivos, Sociales, Culturales, y más."
            GuideStep.NAV_TO_CREATE -> "Crea tu propio evento ✨" to "Vamos a la pantalla de creación para que organices tu primera junta."
            GuideStep.CREATE_EXPLAIN -> "Formulario Simple 📝" to "Aquí defines el título, fecha y ubicación. ¡Es súper rápido!"
            GuideStep.NAV_TO_PROFILE -> "Tu Identidad 😎" to "Ahora vamos a tu perfil para ver tus progresos."
            GuideStep.PROFILE_EXPLAIN -> "Tus Logros 🏆" to "Aquí verás tus medallas, reputación y nivel dentro de la comunidad."
            GuideStep.FINISH -> "¡Estás listo! 🚀" to "Eso es todo. ¡Empieza a conectar con gente genial ahora mismo!"
        }

        val buttonText = when (step) {
            GuideStep.NAV_TO_CREATE -> "Ir a Crear"
            GuideStep.NAV_TO_PROFILE -> "Ir al Perfil"
            GuideStep.FINISH -> "¡Comenzar!"
            else -> "Siguiente"
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${step.ordinal + 1}/${GuideStep.values().size}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Description
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { viewModel.skipGuide() }) {
                        Text("No volver a mostrar")
                    }
                    Button(onClick = { viewModel.nextStep() }) {
                        Text(buttonText)
                    }
                }
            }
        }
    }
}
