
package com.eventos.banana.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Done

import androidx.compose.material.icons.filled.Close
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
import com.eventos.banana.data.ChileCommunesList
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
    onSearchClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onMessagesClick: () -> Unit = {},
    unreadMessages: Int = 0, // Added based on user request
    eventListViewModel: EventListViewModel = viewModel()
) {
    val context = LocalContext.current
    val locations = remember { ChileCommunesList.getRegionsWithCommunes() }
    
    // Preferences for Guide
    val sharedPreferences = remember { context.getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE) }
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val seen = sharedPreferences.getBoolean("home_guide_seen", false)
        if (!seen) {
            showGuide = true
        }
        
        // Round 11: Auto-mark finished events as ratable
        try {
            com.eventos.banana.data.repository.EventRepository().markFinishedEventsAsRatable()
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Failed to mark events", e)
        }
    }
    
    val uiState by eventListViewModel.uiState.collectAsState()
    val profileUiState by sessionViewModel.profileUiState.collectAsState()

    val userCommune = profileUiState.profile?.commune
    val nickname = profileUiState.profile?.nickname

    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var selectedCommune by remember { mutableStateOf<String?>(null) }
    
    // 🔔 A29 DEFAULT LOCATION FILTER
    LaunchedEffect(profileUiState.profile) {
        if (selectedCommune == null && selectedRegion == null) {
            val userProfile = profileUiState.profile
            if (userProfile != null && !userProfile.commune.isNullOrBlank()) {
                 selectedRegion = userProfile.region
                 selectedCommune = userProfile.commune
            }
        }
    }
    
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
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .clickable { onProfileClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("👤", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            // Greeting
                            nickname?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                    actions = {
                        // 👥 FRIENDS
                        IconButton(onClick = onFriendsClick) {
                            Icon(Icons.Default.Person, contentDescription = "Amigos")
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
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onCreateEventClick) { Text("+") }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
            
                // ---------- FILTRO CATEGORÍAS (Horizontales) ----------
                val selectedCategory by eventListViewModel.selectedCategory.collectAsState()
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { eventListViewModel.selectCategory(null) },
                            label = { Text("Todo") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                    
                    items(com.eventos.banana.domain.model.EventType.values().toList()) { type ->
                        FilterChip(
                            selected = selectedCategory == type,
                            onClick = { eventListViewModel.selectCategory(type) },
                            label = { Text("${type.emoji} ${type.displayName}") },
                            leadingIcon = if (selectedCategory == type) {
                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                
                HorizontalDivider()

                // ---------- FILTRO FECHA ----------
                val selectedDateFilter by eventListViewModel.selectedDateFilter.collectAsState()
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(com.eventos.banana.domain.model.DateFilter.values().toList()) { filter ->
                        FilterChip(
                            selected = selectedDateFilter == filter,
                            onClick = { eventListViewModel.selectDateFilter(filter) },
                            label = { Text(filter.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                
                 // ---------- FILTROS REGION/COMUNA ----------
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ... (Dropdowns logic remains same, just visually compacted spacing)
                
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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
                    
                    // Date Filter Helper
                    fun checkDate(eventStart: Long, filter: com.eventos.banana.domain.model.DateFilter): Boolean {
                        val calendar = java.util.Calendar.getInstance()
                        val currentDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
                        val currentYear = calendar.get(java.util.Calendar.YEAR)
                        
                        val eventCalendar = java.util.Calendar.getInstance()
                        eventCalendar.timeInMillis = eventStart
                        val eventDay = eventCalendar.get(java.util.Calendar.DAY_OF_YEAR)
                        val eventYear = eventCalendar.get(java.util.Calendar.YEAR)

                        return when (filter) {
                            com.eventos.banana.domain.model.DateFilter.ALL -> true
                            com.eventos.banana.domain.model.DateFilter.TODAY -> {
                                eventYear == currentYear && eventDay == currentDayOfYear
                            }
                            com.eventos.banana.domain.model.DateFilter.TOMORROW -> {
                                // Simplified tomorrow check
                                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                val tomorrowDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
                                val tomorrowYear = calendar.get(java.util.Calendar.YEAR)
                                eventYear == tomorrowYear && eventDay == tomorrowDay
                            }
                            com.eventos.banana.domain.model.DateFilter.WEEKEND -> {
                                // Check if event is this Friday, Saturday, or Sunday
                                // And we are currently in same week (approx) or upcoming if today is Mon-Thu
                                // Simplified: Is event within next 7 days AND is Fri/Sat/Sun?
                                // Better: Start from today, find next Sunday. Event must be <= Next Sunday && >= Today.
                                // AND Event DayOfWeek must be Fri, Sat, or Sun.
                                
                                val dayOfWeek = eventCalendar.get(java.util.Calendar.DAY_OF_WEEK) // Sun=1, Fri=6, Sat=7
                                val isWeekendDay = dayOfWeek == java.util.Calendar.FRIDAY || 
                                                   dayOfWeek == java.util.Calendar.SATURDAY || 
                                                   dayOfWeek == java.util.Calendar.SUNDAY
                                                   
                                // Ensure it's upcoming (not last year's weekend) - handled by event.endAt > now check generally
                                // But let's restrict to next 5 days to be safe "This Weekend"
                                val diff = eventStart - now
                                val maxDiff = 5 * 24 * 60 * 60 * 1000L // 5 days
                                
                                isWeekendDay && diff >= 0 && diff < maxDiff
                            }
                        }
                    }

                val events = (uiState as EventListUiState.Success).events
                        .filter { event ->
                            event.status == EventStatus.OPEN &&
                                    event.endAt > now &&
                                    (selectedRegion == null || event.region == selectedRegion) &&
                                    (effectiveCommune == null || event.commune == effectiveCommune) &&
                                    (selectedCategory == null || event.eventType == selectedCategory) &&
                                    checkDate(event.startAt, selectedDateFilter)
                        }


                    if (events.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No se encontraron eventos",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Intenta cambiar los filtros",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(events, key = { it.id }) { event ->
                                // Calculate Creator Info
                                val creatorProfile = (uiState as EventListUiState.Success).creatorProfiles[event.creatorId]
                                val creatorName = creatorProfile?.nickname ?: "Usuario"
                                val creatorRating = creatorProfile?.averageRating ?: 0.0
                                val creatorRatingCount = creatorProfile?.ratingCount ?: 0

                                com.eventos.banana.ui.components.BananaEventCard(
                                    event = event,
                                    creatorName = creatorName,
                                    creatorRating = creatorRating,
                                    creatorRatingCount = creatorRatingCount,
                                    onClick = { onEventClick(event.id) },
                                    modifier = Modifier.animateItem()
                                )
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
