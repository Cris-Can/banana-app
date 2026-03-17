
package com.eventos.banana.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex // ➕
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventos.banana.data.ChileCommunesList
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.ui.event.EventListViewModel
import com.eventos.banana.ui.auth.SessionViewModel

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
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
    unreadMessages: Int = 0,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    eventListViewModel: EventListViewModel = hiltViewModel(),
    onMapClick: () -> Unit
) {
    val context = LocalContext.current
    val locations = remember { ChileCommunesList.getRegionsWithCommunes() }
    
    // 🗺️ MAP TOGGLE STATE
    var isMapView by remember { mutableStateOf(false) }

    // 📍 GPS LOGIC
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }
    var hasLocationPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    LaunchedEffect(Unit) {

        // 📍 Request Permission
        if (!hasLocationPermission) {
            permissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    
    // 📍 GET LOCATION
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        eventListViewModel.updateLocation(location.latitude, location.longitude)
                        sessionViewModel.updateProfileLocation(location.latitude, location.longitude)
                        android.util.Log.d("HomeScreen", "📍 Location updated: ${location.latitude}, ${location.longitude}")
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }
    
    val uiState by eventListViewModel.uiState.collectAsState()
    val isLoadingMore by eventListViewModel.isLoadingMore.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EventListUiState.Success) {
            isRefreshing = state.isRefreshing
        } else if (state is EventListUiState.Error) {
            isRefreshing = false
        }
    }
    val profileUiState by sessionViewModel.profileUiState.collectAsState()

    val userCommune = profileUiState.profile?.commune
    val nickname = profileUiState.profile?.nickname

    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var selectedCommune by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 🔔 A29 DEFAULT LOCATION FILTER
    // Only set default if we haven't manually selected yet
    LaunchedEffect(profileUiState.profile) {
        if (selectedCommune == null && selectedRegion == null) {
            val userProfile = profileUiState.profile
            if (userProfile != null && !userProfile.commune.isNullOrBlank()) {
                 selectedRegion = userProfile.region
                 selectedCommune = userProfile.commune
            }
        }
    }
    
    // 📍 UX: Show Toast/Snackbar on Location Update (Background)
    val locationMsg by sessionViewModel.locationUpdateMessage.collectAsState()
    LaunchedEffect(locationMsg) {
        locationMsg?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            sessionViewModel.clearLocationMessage()
        }
    }
    
    // Sync Commune Filter with Server
    LaunchedEffect(selectedCommune) {
        eventListViewModel.updateCommune(selectedCommune)
    }
    
    // If GPS is active and returning events, we might want to relax the stiff filters?
    // For now, keep them. If backend returns nearby events, user must ensure filters match.
    // OR: We could auto-clear filters if GPS is found? 
    // Let's keep manual filters as "Overrides".
    
    val effectiveCommune = selectedCommune
    
    // Map Toggle Logic - Removed shadowing variable
    // onMapClick parameter is now used directly to navigate to World Map

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // ... (Profile Image) ...
                            // Profile photo thumbnail
                            val photoUrl = profileUiState.profile?.profilePictureUrl
                            val isGold = profileUiState.profile?.isGold == true // 👑 Check Gold Status
                            
                            Box(contentAlignment = Alignment.TopStart) {
                                if (!photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = stringResource(com.eventos.banana.R.string.home_cd_profile_photo),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .then(if (isGold) Modifier.border(2.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000))), androidx.compose.foundation.shape.CircleShape) else Modifier)
                                            .clickable { onProfileClick() },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                                            .then(if (isGold) Modifier.border(2.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000))), androidx.compose.foundation.shape.CircleShape) else Modifier)
                                            .clickable { onProfileClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("👤", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                
                                // 👑 Mini Crown
                                if (isGold) {
                                    Text(
                                        "👑", 
                                        style = MaterialTheme.typography.bodySmall, 
                                        modifier = Modifier
                                            .offset(x = (-4).dp, y = (-4).dp)
                                            .graphicsLayer(rotationZ = -15f)
                                            .zIndex(1f)
                                    )
                                }
                            }
                            // Greeting
                            nickname?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                    actions = {
                        // 🌍 MAP TOGGLE
                        IconButton(onClick = { isMapView = !isMapView }) {
                            Icon(
                                imageVector = if (isMapView) Icons.Filled.List else Icons.Filled.LocationOn,
                                contentDescription = if (isMapView) "Ver Lista" else "Ver Mapa"
                            )
                        }

                        // 🔍 SEARCH
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(com.eventos.banana.R.string.home_cd_search))
                        }

                        // 👥 FRIENDS
                        IconButton(onClick = onFriendsClick) {
                            Icon(Icons.Filled.Person, contentDescription = stringResource(com.eventos.banana.R.string.home_cd_friends))
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
                                Icon(Icons.Filled.Email, "Mensajes")
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
                                Icon(Icons.Filled.Notifications, null)
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(onClick = onCreateEventClick) { Text("+") }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            
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
                            label = { Text(stringResource(com.eventos.banana.R.string.home_filter_all)) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == null,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    
                    items(com.eventos.banana.domain.model.EventType.values().toList()) { type ->
                        val isSelected = selectedCategory == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { eventListViewModel.selectCategory(type) },
                            label = { Text("${type.emoji} ${type.localizedName()}") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface, // Clean unselected
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                             border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant, // Subtle border
                                selectedBorderColor = MaterialTheme.colorScheme.primary // No border or matching color
                            )
                        )
                    }
                }
                
                HorizontalDivider()

                // ---------- FILTRO FECHA ----------
                val selectedDateFilter by eventListViewModel.selectedDateFilter.collectAsState()
                val searchRadiusKm by eventListViewModel.searchRadiusKm.collectAsState()
                var showRadiusDialog by remember { mutableStateOf(false) }

                if (showRadiusDialog) {
                    com.eventos.banana.ui.components.RadiusSelectorDialog(
                        currentRadiusKm = searchRadiusKm,
                        onDismiss = { showRadiusDialog = false },
                        onRadiusSelected = { 
                            eventListViewModel.updateRadius(it)
                            // Clear manual location filters to enable radius search
                            selectedRegion = null
                            selectedCommune = null
                            eventListViewModel.updateRegion(null) // ➕ Clear VM Region
                            eventListViewModel.updateCommune(null) // ➕ Clear VM Commune
                        }
                    )
                }
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 📍 Radius Filter
                    item {
                        FilterChip(
                            selected = true, // Always active if using GPS
                            onClick = { showRadiusDialog = true },
                            label = { Text("📍 ${searchRadiusKm} km") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.primary,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = true,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    items(com.eventos.banana.domain.model.DateFilter.values().toList()) { filter ->
                        FilterChip(
                            selected = selectedDateFilter == filter,
                            onClick = { eventListViewModel.selectDateFilter(filter) },
                            label = { Text(filter.localizedName()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                
                 // ---------- FILTROS REGION/COMUNA ----------
            if (!isMapView) {
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
                        value = selectedRegion ?: stringResource(com.eventos.banana.R.string.home_filter_all_regions),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(com.eventos.banana.R.string.home_filter_region)) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false }
                    ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(com.eventos.banana.R.string.home_filter_all_regions)) },
                                onClick = {
                                    selectedRegion = null
                                    selectedCommune = null
                                    eventListViewModel.searchAllRegions()
                                    regionExpanded = false
                                }
                            )
                            locations.keys.sorted().forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region) },
                                    onClick = {
                                        selectedRegion = region
                                        selectedCommune = null
                                        eventListViewModel.updateRegion(region) // ➕
                                        eventListViewModel.updateCommune(null)
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
                            value = selectedCommune ?: stringResource(com.eventos.banana.R.string.home_filter_all_communes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(com.eventos.banana.R.string.home_filter_commune)) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = communeExpanded,
                            onDismissRequest = { communeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(com.eventos.banana.R.string.home_filter_all_communes)) },
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
                } // Column
            } // End of !isMapView Region/Commune block

            // ---------- CONTENIDO PRINCIPAL (LISTA O MAPA) ----------
            when (val state = uiState) {
                is EventListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is EventListUiState.Error -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(state.message) // <-- Using state.message securely
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

                    val events = state.events
                        .filter { event ->
                            event.status == EventStatus.OPEN &&
                                    event.endAt > now &&
                                    (selectedRegion == null || event.region == selectedRegion) &&
                                    (effectiveCommune == null || event.commune == effectiveCommune) &&
                                    (selectedCategory == null || event.eventType == selectedCategory) &&
                                    checkDate(event.startAt, selectedDateFilter)
                        }

                    if (isMapView) {
                        // ---------- VISTA DE MAPA ----------
                        var selectedMapEvent by remember { mutableStateOf<Event?>(null) }
                        
                        // 🚀 OPTIMIZACIÓN SEGURA: Pre-calcular solo datos básicos para evitar lag
                        // Nota: Evitamos crear BitmapDescriptor aquí porque puede fallar si el SDK no está listo
                        val markerDataList = remember(events) {
                            events.mapNotNull { event ->
                                val lat = event.latitude ?: event.exactLatitude ?: 0.0
                                val lng = event.longitude ?: event.exactLongitude ?: 0.0
                                if (lat == 0.0 || lng == 0.0) return@mapNotNull null
                                
                                val hue = when (event.eventType) {
                                    com.eventos.banana.domain.model.EventType.DEPORTES -> 120f // GREEN
                                    com.eventos.banana.domain.model.EventType.SOCIAL -> 270f // VIOLET
                                    com.eventos.banana.domain.model.EventType.CULTURAL -> 330f // ROSE
                                    com.eventos.banana.domain.model.EventType.EDUCATIVO -> 240f // BLUE
                                    com.eventos.banana.domain.model.EventType.JUEGOS -> 180f // CYAN
                                    com.eventos.banana.domain.model.EventType.GASTRONOMIA -> 30f // ORANGE
                                    com.eventos.banana.domain.model.EventType.AIRE_LIBRE -> 150f // GREEN_LIME
                                    else -> 0f // RED
                                }
                                
                                com.eventos.banana.ui.home.MapMarkerInfo(
                                    event = event,
                                    position = com.google.android.gms.maps.model.LatLng(lat, lng),
                                    hue = hue
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val userLoc = state.currentUserLocation
                            val cameraPositionState = com.google.maps.android.compose.rememberCameraPositionState {
                                position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                                    com.google.android.gms.maps.model.LatLng(
                                        userLoc?.latitude ?: -33.4489,
                                        userLoc?.longitude ?: -70.6693
                                    ),
                                    12f
                                )
                            }

                            com.google.maps.android.compose.GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                properties = com.google.maps.android.compose.MapProperties(
                                    isMyLocationEnabled = hasLocationPermission
                                ),
                                uiSettings = com.google.maps.android.compose.MapUiSettings(
                                    myLocationButtonEnabled = true,
                                    zoomControlsEnabled = false,
                                    compassEnabled = true
                                ),
                                onMapClick = { selectedMapEvent = null }
                            ) {
                                markerDataList.forEach { data ->
                                    val markerIcon = remember(data.hue) {
                                        try {
                                            com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(data.hue)
                                        } catch (e: Exception) {
                                            null // Fallback to default
                                        }
                                    }

                                    com.google.maps.android.compose.Marker(
                                        state = com.google.maps.android.compose.MarkerState(position = data.position),
                                        title = data.event.title,
                                        snippet = "${data.event.eventType.emoji} ${data.event.commune}",
                                        onClick = {
                                            selectedMapEvent = data.event
                                            true
                                        },
                                        icon = markerIcon
                                    )
                                }
                            }

                            // 🎨 LEYENDA DEL MAPA
                            var isLegendExpanded by remember { mutableStateOf(false) }
                            
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                onClick = { isLegendExpanded = !isLegendExpanded }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            "Categorías",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (isLegendExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isLegendExpanded) "Colapsar" else "Expandir",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    androidx.compose.animation.AnimatedVisibility(visible = isLegendExpanded) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            com.eventos.banana.domain.model.EventType.values().filter { it != com.eventos.banana.domain.model.EventType.OTRO }.forEach { type ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val hue = when (type) {
                                                        com.eventos.banana.domain.model.EventType.DEPORTES -> 120f
                                                        com.eventos.banana.domain.model.EventType.SOCIAL -> 270f
                                                        com.eventos.banana.domain.model.EventType.CULTURAL -> 330f
                                                        com.eventos.banana.domain.model.EventType.EDUCATIVO -> 240f
                                                        com.eventos.banana.domain.model.EventType.JUEGOS -> 180f
                                                        com.eventos.banana.domain.model.EventType.GASTRONOMIA -> 30f
                                                        com.eventos.banana.domain.model.EventType.AIRE_LIBRE -> 150f
                                                        else -> 0f
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(
                                                                color = androidx.compose.ui.graphics.Color.hsv(hue, 0.7f, 0.9f),
                                                                shape = androidx.compose.foundation.shape.CircleShape
                                                            )
                                                    )
                                                    Text(
                                                        "${type.emoji} ${type.localizedName()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 🃏 CARD OVERLAY al seleccionar un pin
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selectedMapEvent != null,
                                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) +
                                        androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
                                       androidx.compose.animation.fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                selectedMapEvent?.let { event ->
                                    val creatorProfile = state.creatorProfiles[event.creatorId]
                                    val creatorName = creatorProfile?.nickname ?: "Usuario"

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Imagen del evento
                                            if (!event.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = event.imageUrl,
                                                    contentDescription = event.title,
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        event.eventType.emoji,
                                                        style = MaterialTheme.typography.headlineMedium
                                                    )
                                                }
                                            }

                                            // Info del evento
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = event.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${event.eventType.emoji} ${event.eventType.localizedName()}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                // 🕐 Fecha y hora
                                                val dateFormat = remember { java.text.SimpleDateFormat("EEE dd MMM · HH:mm", java.util.Locale("es", "CL")) }
                                                Text(
                                                    text = "🕐 ${dateFormat.format(java.util.Date(event.startAt))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "por $creatorName · ${event.commune}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }

                                            // Botón Ver
                                            FilledTonalButton(
                                                onClick = { onEventClick(event.id) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text("Ver", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }

                                        // Botón cerrar
                                        IconButton(
                                            onClick = { selectedMapEvent = null },
                                            modifier = Modifier
                                                .align(Alignment.End)
                                                .size(24.dp)
                                                .offset(x = (-8).dp, y = (-8).dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Cerrar",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ---------- VISTA LISTA CLÁSICA ----------
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
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    eventListViewModel.refresh()
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp, 
                                        end = 16.dp, 
                                        top = 16.dp, 
                                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp // Space for FAB + Nav Bar
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp) // Rule 5: 12dp separation between cards
                                ) {
                                items(events, key = { it.id }) { event ->
                                    // Calculate Creator Info safely
                                    val creatorProfile = state.creatorProfiles[event.creatorId]
                                    val creatorName = creatorProfile?.nickname ?: "Usuario"
                                    val creatorRating = creatorProfile?.averageRating ?: 0.0
                                    val creatorRatingCount = creatorProfile?.ratingCount ?: 0

                                    com.eventos.banana.ui.components.BananaEventCard(
                                        event = event,
                                        creatorName = creatorName,
                                        creatorRating = creatorRating,
                                        creatorRatingCount = creatorRatingCount,
                                        onClick = { onEventClick(event.id) },
                                        userLocation = state.currentUserLocation,
                                        modifier = Modifier.animateItem(),
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }

                                // 📄 Paginación: Botón "Cargar más"
                                if (state.canLoadMore) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLoadingMore) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            } else {
                                                OutlinedButton(onClick = { eventListViewModel.loadMore() }) {
                                                    Text("Cargar más eventos")
                                                }
                                            }
                                        }
                                    }
                                }
                            } // lazy column
                        } // pull to refresh box
                    } // else events.isEmpty
                    } // if isMapView
                } // when(uiState).Success
            } // when(uiState)
        } // Column modifiers
    } // Box closing
} // Scaffold
} // Fin HomeScreen

data class MapMarkerInfo(
    val event: Event,
    val position: com.google.android.gms.maps.model.LatLng,
    val hue: Float
)
