package com.eventos.banana.ui.admin

import android.content.Context
import java.util.Calendar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ApprovalDialog(
    extState: ExternalEventsUiState,
    externalVm: ExternalEventsViewModel,
    onNavigateToMap: (Double?, Double?) -> Unit,
    categorias: List<String>,
    uriHandler: UriHandler,
    context: Context
) {
    if (!extState.showApprovalDialog) return

    var expandedCategory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { externalVm.dismissApprovalDialog() },
        title = {
            Text("Aprobar evento externo", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Título editable
                OutlinedTextField(
                    value = extState.approvalTitle,
                    onValueChange = { externalVm.updateApprovalTitle(it) },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // URL del evento (clickable + editable)
                if (extState.approvalEventUrl.isNotBlank()) {
                    Text("URL del evento:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (extState.approvalEventUrl.length > 50) extState.approvalEventUrl.take(50) + "…" else extState.approvalEventUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).clickable {
                                try { uriHandler.openUri(extState.approvalEventUrl) } catch (_: Exception) { }
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            try { uriHandler.openUri(extState.approvalEventUrl) } catch (_: Exception) { }
                        }) {
                            Text("🔗 Abrir", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Descripción editable (multiline)
                OutlinedTextField(
                    value = extState.approvalDescription,
                    onValueChange = { externalVm.updateApprovalDescription(it) },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                )

                // Categoría (dropdown)
                Text("Categoría:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                Box {
                    OutlinedButton(
                        onClick = { expandedCategory = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(extState.approvalCategory, modifier = Modifier.weight(1f))
                        Text("▼", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        categorias.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = { externalVm.updateApprovalCategory(cat); expandedCategory = false }
                            )
                        }
                    }
                }

                // Ubicación scrapeada (solo informativa)
                if (extState.approvalScrapedLocation.isNotBlank()) {
                    Text("Ubicación scrapeada:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                    Text(extState.approvalScrapedLocation, style = MaterialTheme.typography.bodySmall)
                }

                // Selector de ubicación en mapa
                val currentMapResult = extState.mapResult
                if (currentMapResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onNavigateToMap(currentMapResult.latitude, currentMapResult.longitude)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("📍 Ubicación definida", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(currentMapResult.address, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("✏️", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToMap(null, null) },
                        colors = CardDefaults.outlinedCardColors()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("🗺️ Seleccionar ubicación en mapa", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider()

                // Selector de fecha de inicio
                val displayDate = extState.approvalStartAt.toLongOrNull()?.let { ts ->
                    try {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "CL"))
                            .format(java.util.Date(ts))
                    } catch (_: Exception) { null }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayDate ?: "No definida",
                        onValueChange = {},
                        label = { Text("Fecha de inicio *") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        singleLine = true,
                        enabled = false
                    )
                    FilledIconButton(onClick = {
                        val now = Calendar.getInstance()
                        extState.approvalStartAt.toLongOrNull()?.let { now.timeInMillis = it }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        cal.set(Calendar.MINUTE, minute)
                                        externalVm.updateApprovalStartAt(cal.timeInMillis.toString())
                                    },
                                    now.get(Calendar.HOUR_OF_DAY),
                                    now.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("📅", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Selector de fecha de término
                val displayEndDate = extState.approvalEndAt.toLongOrNull()?.let { ts ->
                    try {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "CL"))
                            .format(java.util.Date(ts))
                    } catch (_: Exception) { null }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayEndDate ?: "No definida",
                        onValueChange = {},
                        label = { Text("Fecha de término") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        singleLine = true,
                        enabled = false
                    )
                    FilledIconButton(onClick = {
                        val now = Calendar.getInstance()
                        extState.approvalEndAt.toLongOrNull()?.let { now.timeInMillis = it }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        cal.set(Calendar.MINUTE, minute)
                                        externalVm.updateApprovalEndAt(cal.timeInMillis.toString())
                                    },
                                    now.get(Calendar.HOUR_OF_DAY),
                                    now.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("📅", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Selector de fecha de expiración
                val displayExpiresDate = extState.approvalExpiresAt.toLongOrNull()?.let { ts ->
                    try {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "CL"))
                            .format(java.util.Date(ts))
                    } catch (_: Exception) { null }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayExpiresDate ?: "No definida (usará término)",
                        onValueChange = {},
                        label = { Text("Fecha de expiración") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        singleLine = true,
                        enabled = false
                    )
                    FilledIconButton(onClick = {
                        val now = Calendar.getInstance()
                        extState.approvalExpiresAt.toLongOrNull()?.let { now.timeInMillis = it }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        cal.set(Calendar.MINUTE, minute)
                                        externalVm.updateApprovalExpiresAt(cal.timeInMillis.toString())
                                    },
                                    now.get(Calendar.HOUR_OF_DAY),
                                    now.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("📅", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // +18 toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Contenido para mayores de 18 años",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = extState.approvalIsAdultContent,
                        onCheckedChange = { externalVm.updateApprovalIsAdultContent(it) }
                    )
                }

                // ---- fin de selectores de fecha ----

                OutlinedTextField(
                    value = extState.approvalRegion,
                    onValueChange = { externalVm.updateApprovalRegion(it) },
                    label = { Text("Región *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = extState.approvalCommune,
                    onValueChange = { externalVm.updateApprovalCommune(it) },
                    label = { Text("Comuna *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = extState.approvalLat,
                        onValueChange = { externalVm.updateApprovalLat(it) },
                        label = { Text("Latitud *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = extState.approvalLng,
                        onValueChange = { externalVm.updateApprovalLng(it) },
                        label = { Text("Longitud *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = extState.approvalAddress,
                    onValueChange = { externalVm.updateApprovalAddress(it) },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val endAt = extState.approvalEndAt.toLongOrNull()
                        ?: (extState.approvalStartAt.toLongOrNull()?.plus(7200000)
                            ?: System.currentTimeMillis() + 7200000)
                    val expiresAt = extState.approvalExpiresAt.toLongOrNull() ?: endAt
                    externalVm.approveEvent(
                        extState.approvalPendingId,
                        mapOf(
                            "category" to extState.approvalCategory,
                            "region" to extState.approvalRegion,
                            "commune" to extState.approvalCommune,
                            "latitude" to (extState.approvalLat.toDoubleOrNull() ?: 0.0),
                            "longitude" to (extState.approvalLng.toDoubleOrNull() ?: 0.0),
                            "address" to extState.approvalAddress,
                            "startAt" to (extState.approvalStartAt.toLongOrNull() ?: System.currentTimeMillis()),
                            "endAt" to endAt,
                            "expiresAt" to expiresAt,
                            "isAdultContent" to extState.approvalIsAdultContent
                        )
                    )
                    externalVm.dismissApprovalDialog()
                }
            ) {
                Text("✓ Aprobar")
            }
        },
        dismissButton = {
            TextButton(onClick = { externalVm.dismissApprovalDialog() }) {
                Text("Cancelar")
            }
        }
    )
}
