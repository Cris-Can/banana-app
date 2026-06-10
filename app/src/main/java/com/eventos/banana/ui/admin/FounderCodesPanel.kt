package com.eventos.banana.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FounderCodesPanel(
    toolsState: AdminToolsUiState,
    toolsVm: AdminToolsViewModel,
    currentUserId: String,
    durationOptions: List<Pair<Int?, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gestión de Invitaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (toolsState.generatedCode != null) {
                Text("Nuevo Código Founder Generado:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = toolsState.generatedCode!!,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                val durText = durationOptions.find { it.first == toolsState.selectedDurationDays }?.second ?: "Eterno"
                Text("Duración asignada: $durText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { toolsVm.clearGeneratedCode() }) {
                    Text("Generar Otro")
                }
            } else {
                Text("Elegir duración de la suscripción regalo:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(durationOptions, key = { it.first ?: 0 }) { (days, label) ->
                        val isSelected = days == toolsState.selectedDurationDays
                        Surface(
                            modifier = Modifier.clickable { toolsVm.setSelectedDurationDays(days) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { toolsVm.generateFounderCode(currentUserId) },
                    enabled = !toolsState.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (toolsState.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Generar Código Founder")
                    }
                }
            }
        }
    }
}
