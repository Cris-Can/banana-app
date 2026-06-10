package com.eventos.banana.ui.admin

import java.net.URI
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExternalEventsPanel(
    extState: ExternalEventsUiState,
    externalVm: ExternalEventsViewModel,
    onNavigateToMap: (Double?, Double?) -> Unit
) {
    var expandedInstaModel by remember { mutableStateOf(false) }

    HorizontalDivider()
    Text(
        "🌐 Eventos Externos",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 1. Setup Bot
            if (!extState.externalBotReady) {
                OutlinedButton(
                    onClick = { externalVm.setupExternalBot() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configurar Bot de Eventos")
                }
            }

            // 2. Add Source
            Text(
                "Fuentes de scraping",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = extState.newSourceUrl,
                    onValueChange = { externalVm.updateNewSourceUrl(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("URL de la fuente") },
                    singleLine = true
                )
                Button(
                    onClick = { externalVm.addSource(extState.newSourceUrl, extState.newSourceName) },
                    enabled = !extState.isLoadingSources && extState.newSourceUrl.isNotBlank()
                ) {
                    Text("+")
                }
            }

            // 3. Sources List
            if (extState.isLoadingSources) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally)
                )
            } else if (extState.externalSources.isEmpty()) {
                Text(
                    "Sin fuentes registradas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                extState.externalSources.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source["name"] as? String ?: (source["url"] as? String ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            val url = source["url"] as? String ?: ""
                            if (url.isNotBlank()) {
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { externalVm.removeSource(source["id"] as? String ?: "") }
                        ) {
                            Text("🗑", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 4. Run Scheduler + Model Selector
            Button(
                onClick = { externalVm.runScheduler(extState.selectedModel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !extState.isRunningScheduler
            ) {
                if (extState.isRunningScheduler) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("🔄 Revisar fuentes ahora")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Modelo IA:", style = MaterialTheme.typography.bodySmall)
                Box(modifier = Modifier.weight(1f)) {
                    var expandedModel by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { expandedModel = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !extState.isRunningScheduler
                    ) {
                        Text(
                            text = when (extState.selectedModel) {
                                "gemini-flash" -> "⚡ Gemini Flash"
                                "groq-llama3-70b" -> "🦙 Groq Llama 3 70B"
                                "groq-qwen3-32b" -> "🐉 Groq Qwen3 32B"
                                else -> extState.selectedModel
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = expandedModel,
                        onDismissRequest = { expandedModel = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("⚡ Gemini Flash") },
                            onClick = { externalVm.selectModel("gemini-flash"); expandedModel = false }
                        )
                        DropdownMenuItem(
                            text = { Text("🦙 Groq Llama 3 70B") },
                            onClick = { externalVm.selectModel("groq-llama3-70b"); expandedModel = false }
                        )
                        DropdownMenuItem(
                            text = { Text("🐉 Groq Qwen3 32B") },
                            onClick = { externalVm.selectModel("groq-qwen3-32b"); expandedModel = false }
                        )
                    }
                }
            }

            // 5. Pending Events
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pendientes (${extState.pendingExternalEvents.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (extState.isLoadingPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally)
                )
            } else if (extState.pendingExternalEvents.isEmpty()) {
                Text(
                    "Sin eventos pendientes de revisión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Group events by sourceUrl
                val grouped = extState.pendingExternalEvents.groupBy { event ->
                    val url = event["sourceUrl"] as? String ?: event["sourceId"] as? String ?: "desconocido"
                    try {
                        URI(url).host?.removePrefix("www.") ?: url
                    } catch (_: Exception) {
                        if (url.length > 40) url.take(40) + "…" else url
                    }
                }

                grouped.forEach { (sourceName, events) ->
                    var isExpanded by remember(sourceName) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isExpanded) "▼" else "▶",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(sourceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${events.size} evento${if (events.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isExpanded) {
                                Text("▲", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (isExpanded) {
                        events.forEach { event ->
                            val eventId = event["id"] as? String ?: return@forEach
                            Spacer(Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = event["title"] as? String ?: "Sin título",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val desc = event["description"] as? String ?: ""
                                    if (desc.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                externalVm.showApprovalDialog(event)
                                                extState.mapResult?.let { loc ->
                                                    externalVm.onMapResult(loc)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("✓ Aprobar")
                                        }
                                        Button(
                                            onClick = { externalVm.rejectEvent(eventId, null) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("✗ Rechazar")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 6. Instagram Discovery
            HorizontalDivider()
            Text(
                "Instagram",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = extState.instagramUrl,
                onValueChange = { externalVm.updateInstagramUrl(it) },
                label = { Text("Pegar URL de Instagram") },
                placeholder = { Text("https://www.instagram.com/p/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !extState.isProcessingInstagram
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { externalVm.submitInstagramUrl(extState.instagramUrl, extState.selectedModel) },
                    modifier = Modifier.weight(1f),
                    enabled = extState.instagramUrl.isNotBlank() && !extState.isProcessingInstagram
                ) {
                    if (extState.isProcessingInstagram) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("📷 Procesar")
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { expandedInstaModel = true },
                        enabled = !extState.isProcessingInstagram
                    ) {
                        Text(
                            when (extState.selectedModel) {
                                "gemini-flash" -> "⚡"
                                "groq-llama3-70b" -> "🦙"
                                "groq-qwen3-32b" -> "🐉"
                                else -> "?"
                            }
                        )
                        Text("▼", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = expandedInstaModel,
                        onDismissRequest = { expandedInstaModel = false }
                    ) {
                        DropdownMenuItem(text = { Text("⚡ Gemini Flash") }, onClick = { externalVm.selectModel("gemini-flash"); expandedInstaModel = false })
                        DropdownMenuItem(text = { Text("🦙 Groq Llama 3 70B") }, onClick = { externalVm.selectModel("groq-llama3-70b"); expandedInstaModel = false })
                        DropdownMenuItem(text = { Text("🐉 Groq Qwen3 32B") }, onClick = { externalVm.selectModel("groq-qwen3-32b"); expandedInstaModel = false })
                    }
                }
            }

            // 8. Created Events
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Eventos Creados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { externalVm.loadCreatedExternalEvents() }) {
                    Text("📋 Cargar")
                }
            }

            if (extState.isLoadingCreated) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally)
                )
            } else if (extState.createdExternalEvents.isEmpty()) {
                Text(
                    "Sin eventos creados. Presiona 'Cargar'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                extState.createdExternalEvents.forEach { event ->
                    val eventId = event["id"] as? String ?: return@forEach
                    val status = event["status"] as? String ?: ""
                    val isCancelled = status == "CANCELLED"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCancelled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event["title"] as? String ?: "Sin título",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCancelled) {
                                        Text(
                                            "CANCELADO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val createdAt = event["createdAt"] as? Number ?: 0
                                    val dateStr = try {
                                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "CL"))
                                            .format(java.util.Date(createdAt.toLong()))
                                    } catch (_: Exception) { "" }
                                    if (dateStr.isNotBlank()) {
                                        Text(
                                            "Creado: $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (!isCancelled) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { externalVm.showDeleteConfirm(eventId, event["title"] as? String ?: "este evento") },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🚫 Cancelar", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = { externalVm.deleteEvent(eventId, true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗑 Eliminar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // 7. Manual Publish
            HorizontalDivider()
            var showManualForm by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showManualForm = !showManualForm },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (showManualForm) "▲ Ocultar formulario manual"
                    else "▼ Publicar evento manualmente"
                )
            }
            if (showManualForm) {
                var manualTitle by remember { mutableStateOf("") }
                var manualRegion by remember { mutableStateOf("") }
                var manualCommune by remember { mutableStateOf("") }
                var manualAddress by remember { mutableStateOf("") }
                var manualLat by remember { mutableStateOf("") }
                var manualLng by remember { mutableStateOf("") }
                var manualStartAt by remember { mutableStateOf("") }
                var manualAdultContent by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Título *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualRegion,
                        onValueChange = { manualRegion = it },
                        label = { Text("Región *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualCommune,
                        onValueChange = { manualCommune = it },
                        label = { Text("Comuna *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = { manualAddress = it },
                        label = { Text("Dirección") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualLat,
                            onValueChange = { manualLat = it },
                            label = { Text("Latitud") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualLng,
                            onValueChange = { manualLng = it },
                            label = { Text("Longitud") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = manualStartAt,
                        onValueChange = { manualStartAt = it },
                        label = { Text("Fecha inicio (timestamp ms)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Contenido +18",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = manualAdultContent,
                                    onCheckedChange = { manualAdultContent = it }
                                )
                            }
                    Button(
                        onClick = {
                            externalVm.publishEventManually(
                                title = manualTitle,
                                region = manualRegion,
                                commune = manualCommune,
                                address = manualAddress,
                                latitude = manualLat.toDoubleOrNull() ?: 0.0,
                                longitude = manualLng.toDoubleOrNull() ?: 0.0,
                                startAt = manualStartAt.toLongOrNull() ?: System.currentTimeMillis(),
                                endAt = null,
                                expiresAt = null,
                                isAdultContent = manualAdultContent
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualTitle.isNotBlank() && manualRegion.isNotBlank() && manualCommune.isNotBlank()
                    ) {
                        Text("Publicar Evento")
                    }
                }
            }
        }
    }

    // Dialog de confirmación para eliminar/cancelar evento
    if (extState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!extState.isDeletingEvent) externalVm.dismissDeleteConfirm() },
            title = { Text("¿Eliminar evento?") },
            text = {
                Text("¿Qué acción quieres realizar con \"${extState.eventToDeleteTitle ?: ""}\"?")
            },
            confirmButton = {
                Button(
                    onClick = { extState.eventToDeleteId?.let { externalVm.deleteEvent(it, false) } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !extState.isDeletingEvent
                ) {
                    if (extState.isDeletingEvent) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("🚫 Cancelar evento")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { extState.eventToDeleteId?.let { externalVm.deleteEvent(it, true) } },
                        enabled = !extState.isDeletingEvent
                    ) {
                        if (extState.isDeletingEvent) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("🗑 Eliminar permanentemente", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = { externalVm.dismissDeleteConfirm() },
                        enabled = !extState.isDeletingEvent
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}
