package com.eventos.banana.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventos.banana.data.local.regionsWithCommunes
import com.eventos.banana.domain.model.CreateEventUiState
import com.eventos.banana.domain.model.Event

@Composable
fun CreateEventScreen(
    creatorId: String,
    uiState: CreateEventUiState,
    onCreateEvent: (Event) -> Unit,
    onSuccess: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var commune by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }

    var showRegionMenu by remember { mutableStateOf(false) }
    var showCommuneMenu by remember { mutableStateOf(false) }

    val communes = regionsWithCommunes[region] ?: emptyList()
    LaunchedEffect(Unit) {
        println("REGIONES = ${regionsWithCommunes.keys}")
    }


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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Crear evento", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Categoría") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

        // -------- REGIÓN --------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRegionMenu = true }
            ) {
                TextField(
                    value = region,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Región") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                DropdownMenu(
                    expanded = showRegionMenu,
                    onDismissRequest = { showRegionMenu = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    regionsWithCommunes.keys.forEach { regionName ->
                        DropdownMenuItem(
                            text = { Text(regionName) },
                            onClick = {
                                region = regionName
                                commune = ""
                                showRegionMenu = false
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(8.dp))

        // -------- COMUNA --------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (communes.isNotEmpty()) {
                            showCommuneMenu = true
                        }
                    }
            ) {
                TextField(
                    value = commune,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Comuna") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                DropdownMenu(
                    expanded = showCommuneMenu,
                    onDismissRequest = { showCommuneMenu = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    communes.forEach { communeName ->
                        DropdownMenuItem(
                            text = { Text(communeName) },
                            onClick = {
                                commune = communeName
                                showCommuneMenu = false
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = maxParticipants,
                onValueChange = { value ->
                    if (value.all { it.isDigit() }) {
                        maxParticipants = value
                    }
                },
                label = { Text("Cupos máximos") },
                modifier = Modifier.fillMaxWidth()
            )
            if (maxParticipants.isNotEmpty() && maxParticipants.toIntOrNull() == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ingresa solo números",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }



            Spacer(modifier = Modifier.height(16.dp))

            Button(
                enabled = !uiState.isLoading &&
                        title.isNotBlank() &&
                        description.isNotBlank() &&
                        category.isNotBlank() &&
                        region.isNotBlank() &&
                        commune.isNotBlank() &&
                        maxParticipants.toIntOrNull()?.let { it > 0 } == true
                ,
                onClick = {
                    if (
                        title.isBlank() ||
                        description.isBlank() ||
                        category.isBlank() ||
                        region.isBlank() ||
                        commune.isBlank() ||
                        maxParticipants.toIntOrNull() == null ||
                        maxParticipants.toInt() <= 0
                    ) return@Button

                    onCreateEvent(
                        Event(
                            creatorId = creatorId,
                            title = title,
                            description = description,
                            category = category,
                            region = region,
                            commune = commune,
                            maxParticipants = maxParticipants.toInt(),
                            eventTimestamp = System.currentTimeMillis()
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
