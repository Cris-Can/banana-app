package com.eventos.banana.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.data.local.regionsWithCommunes
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.viewmodel.EventListViewModel
import com.eventos.banana.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionViewModel: SessionViewModel,
    onCreateEventClick: () -> Unit,
    unreadNotifications: Int,
    onEventClick: (String) -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    eventListViewModel: EventListViewModel = viewModel()
) {
    val uiState by eventListViewModel.uiState.collectAsState()

    // ---------- PERFIL ----------
    val profileUiState by sessionViewModel.profileUiState.collectAsState()
    val userCommune = profileUiState.profile?.commune
    val nickname = profileUiState.profile?.nickname

    // ---------- FILTROS MANUALES ----------
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var selectedCommune by remember { mutableStateOf<String?>(null) }

    // 👉 COMUNA EFECTIVA (manual tiene prioridad)
    val effectiveCommune = selectedCommune ?: userCommune

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Eventos",
                            style = MaterialTheme.typography.titleMedium
                        )

                        nickname?.let {
                            Text(
                                text = "Hola, $it 👋",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge {
                                        Text(unreadNotifications.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notificaciones"
                            )
                        }
                    }

                    TextButton(onClick = onProfileClick) {
                        Text("Perfil")
                    }

                    TextButton(onClick = { sessionViewModel.logout() }) {
                        Text("Cerrar sesión")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateEventClick) {
                Text("+")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // ================= FILTROS =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                var regionExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = regionExpanded,
                    onExpandedChange = { regionExpanded = !regionExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRegion ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Región") },
                        placeholder = { Text("Selecciona región") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false }
                    ) {
                        regionsWithCommunes.keys.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region) },
                                onClick = {
                                    selectedRegion = region
                                    selectedCommune = null
                                    regionExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                var communeExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = communeExpanded,
                    onExpandedChange = {
                        if (selectedRegion != null) {
                            communeExpanded = !communeExpanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = selectedCommune ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedRegion != null,
                        label = { Text("Comuna") },
                        placeholder = { Text("Selecciona comuna") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = communeExpanded,
                        onDismissRequest = { communeExpanded = false }
                    ) {
                        selectedRegion?.let { region ->
                            regionsWithCommunes[region]?.forEach { commune ->
                                DropdownMenuItem(
                                    text = { Text(commune) },
                                    onClick = {
                                        selectedCommune = commune
                                        communeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ================= CONTENIDO =================
            when (uiState) {

                is EventListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is EventListUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((uiState as EventListUiState.Error).message)
                    }
                }

                is EventListUiState.Success -> {
                    val allEvents = (uiState as EventListUiState.Success).events

                    val filteredEvents = allEvents.filter { event ->
                        (selectedRegion == null || event.region == selectedRegion) &&
                                (effectiveCommune == null || event.commune == effectiveCommune)
                    }

                    when {
                        allEvents.isEmpty() -> {
                            EmptyState(
                                title = "Aún no hay eventos",
                                message = "Sé el primero en crear un evento.",
                                buttonText = "Crear evento",
                                onActionClick = onCreateEventClick
                            )
                        }

                        filteredEvents.isEmpty() -> {
                            EmptyState(
                                title = "No hay resultados",
                                message = "Prueba cambiando los filtros.",
                                buttonText = "Limpiar filtros",
                                onActionClick = {
                                    selectedRegion = null
                                    selectedCommune = null
                                }
                            )
                        }

                        else -> {
                            EventList(
                                events = filteredEvents,
                                onEventClick = onEventClick
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= COMPONENTES =================

@Composable
private fun EventList(
    events: List<Event>,
    onEventClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(events) { event ->
            EventItem(
                event = event,
                onClick = { onEventClick(event.id) }
            )
        }
    }
}

@Composable
private fun EventItem(
    event: Event,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(event.description)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${event.region} • ${event.commune}")
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    buttonText: String,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onActionClick) {
            Text(buttonText)
        }
    }
}
