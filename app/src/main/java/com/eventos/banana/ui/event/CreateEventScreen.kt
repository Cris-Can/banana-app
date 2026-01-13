package com.eventos.banana.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventos.banana.data.local.regionsWithCommunes
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.JoinQuestion
import java.util.UUID
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    creatorId: String,
    uiState: CreateEventUiState,
    onCreateEvent: (Event) -> Unit,
    onSuccess: () -> Unit
) {
    // ================= ESTADO BÁSICO =================
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var commune by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }

    // ================= A7.7 — PREGUNTAS =================
    var questions by remember { mutableStateOf<List<JoinQuestion>>(emptyList()) }

    // ================= DROPDOWNS =================

    var showCommuneMenu by remember { mutableStateOf(false) }
    var communeExpanded by remember { mutableStateOf(false) }
    var regionExpanded by remember { mutableStateOf(false) }
    var showRegionMenu by remember { mutableStateOf(false) }


    val communes = regionsWithCommunes[region] ?: emptyList()
    val scrollState = rememberScrollState()


    // ================= ÉXITO =================
    LaunchedEffect(uiState) {
        if (uiState.success) onSuccess()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {

            Text("Crear evento", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(16.dp))

            // ================= CAMPOS BÁSICOS =================
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Categoría") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ================= REGIÓN =================

            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { regionExpanded = !regionExpanded }
            ) {
                OutlinedTextField(
                    value = region,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Región") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false }
                ) {
                    regionsWithCommunes.keys.forEach { regionName ->
                        DropdownMenuItem(
                            text = { Text(regionName) },
                            onClick = {
                                region = regionName
                                commune = ""
                                regionExpanded = false
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(8.dp))

            // ================= COMUNA =================

            ExposedDropdownMenuBox(
                expanded = communeExpanded,
                onExpandedChange = {
                    if (communes.isNotEmpty()) {
                        communeExpanded = !communeExpanded
                    }
                }
            ) {
                OutlinedTextField(
                    value = commune,
                    onValueChange = {},
                    readOnly = true,
                    enabled = communes.isNotEmpty(),
                    label = { Text("Comuna") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = communeExpanded,
                    onDismissRequest = { communeExpanded = false }
                ) {
                    communes.forEach { communeName ->
                        DropdownMenuItem(
                            text = { Text(communeName) },
                            onClick = {
                                commune = communeName
                                communeExpanded = false
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(8.dp))

            // ================= CUPOS =================
            OutlinedTextField(
                value = maxParticipants,
                onValueChange = { if (it.all(Char::isDigit)) maxParticipants = it },
                label = { Text("Cupos máximos") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // =====================================================
            // ================= A7.7 — PREGUNTAS =================
            // =====================================================

            Text(
                text = "Preguntas para solicitar acceso (opcional)",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            questions.forEachIndexed { index, question ->

                OutlinedTextField(
                    value = question.text,
                    onValueChange = { newText ->
                        questions = questions.toMutableList().also {
                            it[index] = question.copy(text = newText)
                        }
                    },
                    label = { Text("Pregunta ${index + 1}") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = question.required,
                        onCheckedChange = { checked ->
                            questions = questions.toMutableList().also {
                                it[index] = question.copy(required = checked)
                            }
                        }
                    )
                    Text("Obligatoria")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    questions = questions + JoinQuestion(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        required = false
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar pregunta")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ================= CREAR EVENTO =================
            Button(
                enabled = !uiState.isLoading &&
                        title.isNotBlank() &&
                        description.isNotBlank() &&
                        category.isNotBlank() &&
                        region.isNotBlank() &&
                        commune.isNotBlank() &&
                        maxParticipants.toIntOrNull()?.let { it > 0 } == true,
                onClick = {
                    onCreateEvent(
                        Event(
                            creatorId = creatorId,
                            title = title,
                            description = description,
                            category = category,
                            region = region,
                            commune = commune,
                            maxParticipants = maxParticipants.toInt(),
                            eventTimestamp = System.currentTimeMillis(),
                            joinQuestions = questions
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Crear evento")
                }
            }

            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
