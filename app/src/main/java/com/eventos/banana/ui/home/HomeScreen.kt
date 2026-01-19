
package com.eventos.banana.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.data.location.ChileLocationProvider
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.domain.model.EventStatus
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
    onMessagesClick: () -> Unit = {},
    unreadMessages: Int = 0, // Added based on user request
    eventListViewModel: EventListViewModel = viewModel()
) {
    val context = LocalContext.current
    val locations = remember { ChileLocationProvider.getRegionsWithCommunes(context) }
    
    // Preferences for Guide
    val sharedPreferences = remember { context.getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE) }
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val seen = sharedPreferences.getBoolean("home_guide_seen", false)
        if (!seen) {
            showGuide = true
        }
    }

    val uiState by eventListViewModel.uiState.collectAsState()
    val profileUiState by sessionViewModel.profileUiState.collectAsState()

    val userCommune = profileUiState.profile?.commune
    val nickname = profileUiState.profile?.nickname

    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var selectedCommune by remember { mutableStateOf<String?>(null) }

    val effectiveCommune = if (selectedRegion == null) null else (selectedCommune ?: userCommune)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Profile photo thumbnail
                            // Profile photo thumbnail
                            val photoUrl = profileUiState.profile?.profilePictureUrl
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Foto de perfil",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable { onProfileClick() },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer) // Distinct color (Purple-ish in default theme)
                                        .clickable { onProfileClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("👤", style = MaterialTheme.typography.titleMedium) // Emoticon as requested
                                }
                            }
                            // Greeting
                            nickname?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                    actions = {
                        // Help button
                        IconButton(onClick = { showGuide = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Ayuda"
                            )
                        }
                        // Messages button
                        IconButton(onClick = onMessagesClick) {
                            BadgedBox(
                                badge = {
                                    if (unreadMessages > 0) {
                                        Badge { Text(unreadMessages.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Email, "Mensajes")
                            }
                        }
                        // Notifications button
                        IconButton(onClick = onNotificationsClick) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) {
                                        Badge { Text(unreadNotifications.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, null)
                            }
                        }
                        TextButton(onClick = { sessionViewModel.logout() }) { Text("Salir") }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onCreateEventClick) { Text("+") }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                 // ... (Rest of content remains same, just need to carefuly check indentation if I replaced the whole file but I will try to use targeted replacement if possible, but structure changed)
                 // RE-STRATEGY: I will replace the START of the file down to the scaffold content start to inject the Box wrapper.
                 // Wait, I can't leave open braces.
                 // I will replace the WHOLE file to be safe because I am wrapping Scaffold in Box and adding imports.
                 
                 // ---------- FILTROS ----------
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Filtros", style = MaterialTheme.typography.titleMedium)
                
                var regionExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = regionExpanded,
                    onExpandedChange = { regionExpanded = !regionExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRegion ?: "Todas las regiones",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Región") },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false }
                    ) {
                            DropdownMenuItem(
                                text = { Text("Todas las regiones") },
                                onClick = {
                                    selectedRegion = null
                                    selectedCommune = null
                                    regionExpanded = false
                                }
                            )
                            locations.keys.sorted().forEach { region ->
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

                val communes = selectedRegion?.let { locations[it] }.orEmpty()

                if (communes.isNotEmpty()) {
                    var communeExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = communeExpanded,
                        onExpandedChange = {
                            communeExpanded = !communeExpanded
                        }
                    ) {
                        OutlinedTextField(
                            value = selectedCommune ?: "Todas las comunas",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Comuna") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = communeExpanded,
                            onDismissRequest = { communeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas las comunas") },
                                onClick = {
                                    selectedCommune = null
                                    communeExpanded = false
                                }
                            )
                            communes.sorted().forEach { commune ->
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

            // ---------- LISTA ----------
            when (uiState) {
                is EventListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is EventListUiState.Error -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text((uiState as EventListUiState.Error).message)
                    }
                }

                is EventListUiState.Success -> {
                    val now = System.currentTimeMillis()

                    val events = (uiState as EventListUiState.Success).events
                        .filter { event ->
                            event.status == EventStatus.OPEN &&
                                    event.endAt > now &&
                                    (selectedRegion == null || event.region == selectedRegion) &&
                                    (effectiveCommune == null || event.commune == effectiveCommune)
                        }



                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(events) { event ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEventClick(event.id) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column {
                                    if (!event.imageUrl.isNullOrBlank()) {
                                        coil.compose.AsyncImage(
                                            model = event.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            event.title, 
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        
                                        // Creator Name
                                        val creatorName = (uiState as EventListUiState.Success).creatorNicknames[event.creatorId] ?: "Usuario"
                                        Text(
                                            "Organizado por: $creatorName",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        
                                        Spacer(Modifier.height(4.dp))
                                        
                                        Text(
                                            "${event.region} • ${event.commune}", 
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                                            val startStr = dateFormat.format(java.util.Date(event.startAt))
                                            val endStr = dateFormat.format(java.util.Date(event.endAt))
                                            
                                            Text(
                                                "📅 $startStr - $endStr",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            Text(
                                                "Participar →",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
    } // End Scaffold
             
        if (showGuide) {
            HomeGuideOverlay(onDismiss = {
                showGuide = false
                sharedPreferences.edit().putBoolean("home_guide_seen", true).apply()
            })
        }
    }
}
