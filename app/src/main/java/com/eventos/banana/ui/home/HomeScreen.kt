
package com.eventos.banana.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.maps.android.compose.clustering.*
import com.google.maps.android.compose.Circle
import com.google.maps.android.clustering.ClusterItem

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventListUiState
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
    val scope = rememberCoroutineScope()

    val sharedPrefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("banana_prefs", android.content.Context.MODE_PRIVATE)
    val hasSeenHomeGuide = remember { mutableStateOf(
        sharedPrefs.getBoolean("home_guide_seen", false)
    ) }
    
    // 🗺️ MAP STATE (Elevated to be accessible from FAB)
    // Rule 1: No more hardcoded Santiago. Initialize with world view or profile location if already loaded.
    val cameraPositionState = com.google.maps.android.compose.rememberCameraPositionState {
        val profile = sessionViewModel.profileUiState.value.profile
        val initialLat = profile?.latitude ?: 0.0
        val initialLng = profile?.longitude ?: 0.0
        
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(initialLat, initialLng),
            if (initialLat != 0.0) 12f else 2f // 🌍 World view if no location
        )
    }
    
    // Flag to ensure we only auto-center once on app start
    var hasCenteredCamera by remember { mutableStateOf(false) }
    
    // 🗺️ MAP TOGGLE STATE — Mapa es la vista por defecto
    var isMapView by remember { mutableStateOf(true) }

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
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    eventListViewModel.updateLocation(location.latitude, location.longitude)
                    sessionViewModel.updateProfileLocation(location.latitude, location.longitude)
                }
            } catch (e: SecurityException) {
                // Ignore
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // re-lanzar para respetar cancelación
            }
        }
    }
    
    val uiState by eventListViewModel.uiState.collectAsStateWithLifecycle()
    val isLoadingMore by eventListViewModel.isLoadingMore.collectAsStateWithLifecycle()
    
    // 👤 Centralized User ID for privacy checks
    val currentUserId = remember { sessionViewModel.currentUserId() }

    var isRefreshing by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EventListUiState.Success) {
            isRefreshing = state.isRefreshing
        } else if (state is EventListUiState.Error) {
            isRefreshing = false
        }
    }
    val profileUiState by sessionViewModel.profileUiState.collectAsStateWithLifecycle()
    val nickname = profileUiState.profile?.nickname
    val isIdentityVerified = profileUiState.profile?.identityVerified ?: false
    LaunchedEffect(isIdentityVerified) {
        eventListViewModel.setIdentityVerified(isIdentityVerified)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    
    // 📍 UX: Mostrar Snackbar con mensajes de ubicación
    val locationMsg by sessionViewModel.locationUpdateMessage.collectAsStateWithLifecycle()
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
                        // 🗺️ MAP / LIST TOGGLE
                        IconButton(onClick = { isMapView = !isMapView }) {
                            Text(
                                text = if (isMapView) "📋" else "🗺️",
                                fontSize = 20.sp
                            )
                        }

                        // 🔍 SEARCH
                        IconButton(onClick = onSearchClick) {
                            Text("🔍", fontSize = 20.sp)
                        }

                        // 👥 FRIENDS
                        IconButton(onClick = onFriendsClick) {
                            Text("👥", fontSize = 20.sp)
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
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 🎯 BOTÓN CENTRAR (Solo en Mapa)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isMapView,
                        enter = androidx.compose.animation.scaleIn(),
                        exit = androidx.compose.animation.scaleOut()
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                val userLoc = uiState.let { if (it is EventListUiState.Success) it.currentUserLocation else null }
                                userLoc?.let {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                                com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude),
                                                15f
                                            )
                                        )
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                        ) {
                            Text("🎯", fontSize = 18.sp)
                        }
                    }

                    // ➕ BOTÓN CREAR (Siempre visible)
                    FloatingActionButton(
                        onClick = onCreateEventClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Text("➕", fontSize = 24.sp)
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            
                // ---------- FILTRO CATEGORÍAS (Horizontales) ----------
                val selectedCategory = uiState.selectedCategory
                val selectedDateFilter = uiState.selectedDateFilter
                val searchRadiusKm = profileUiState.profile?.searchRadiusKm ?: 20

                var showRadiusDialog by remember { mutableStateOf(false) }

                if (showRadiusDialog) {
                    com.eventos.banana.ui.components.RadiusSelectorDialog(
                        currentRadiusKm = searchRadiusKm,
                        onDismiss = { showRadiusDialog = false },
                        onRadiusSelected = { newRadius ->
                            eventListViewModel.updateRadius(newRadius)
                        }
                    )
                }

                com.eventos.banana.ui.components.FilterBar(
                    selectedCategory = selectedCategory,
                    selectedDateFilter = selectedDateFilter,
                    searchRadiusKm = searchRadiusKm,
                    onCategoryClick = { eventListViewModel.selectCategory(it) },
                    onDateFilterClick = { eventListViewModel.selectDateFilter(it) },
                    onRadiusClick = { showRadiusDialog = true }
                )

                // ---------- INDICADOR DE UBICACIÓN (solo en vista Lista) ----------
                if (!isMapView) {
                    val userLoc = (uiState as? EventListUiState.Success)?.currentUserLocation
                    com.eventos.banana.ui.components.LocationIndicator(
                        userLocation = userLoc,
                        searchRadiusKm = searchRadiusKm,
                        onClick = { showRadiusDialog = true }
                    )
                }

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
                    val events = state.events


                    if (isMapView) {
                        // ---------- VISTA DE MAPA ----------
                        var selectedMapEvent by remember { mutableStateOf<Event?>(null) }
                        
                        // Optimización Refinada: Caché de LatLng vinculado a la lista de eventos actual
                        // Se reinicia cuando la lista total cambia para evitar crecimiento infinito de memoria.
                        val latLngCache = remember(events) { mutableMapOf<String, com.google.android.gms.maps.model.LatLng>() }
                        
                        // derivedStateOf garantiza que el mapa solo se notifique si la lista final de markers procesados cambia,
                        // evitando recomposiciones por cambios en otros campos del uiState (ej. isRefreshing).
                        val markerDataList by remember(events, currentUserId) {
                            derivedStateOf {
                                events.mapNotNull { event ->
                                    // 🔐 LÓGICA DE PRIVACIDAD
                                    val isApproved = currentUserId.isNotEmpty() && event.approvedParticipants.contains(currentUserId)
                                    val isCreator = currentUserId.isNotEmpty() && event.creatorId == currentUserId
                                    val useExact = event.isPublic || isCreator || isApproved
                                    
                                    val lat = if (useExact) (event.exactLatitude ?: event.latitude ?: 0.0) else (event.latitude ?: 0.0)
                                    val lng = if (useExact) (event.exactLongitude ?: event.longitude ?: 0.0) else (event.longitude ?: 0.0)
                                    if (lat == 0.0 || lng == 0.0) return@mapNotNull null
                                    
                                    // CacheKey robusta: Incluye coordenadas para detectar cambios en la posición del mismo evento (ej. edición)
                                    val cacheKey = "${event.id}_${lat}_${lng}_${useExact}"
                                    val pos = latLngCache.getOrPut(cacheKey) {
                                        com.google.android.gms.maps.model.LatLng(lat, lng)
                                    }
                                    
                                    MapMarkerInfo(
                                        event = event,
                                        markerPosition = pos,
                                        hue = getEventHue(event.eventType),
                                        useExact = useExact
                                    )
                                }
                            }
                        }
                        
                        val circleMarkerData = remember(markerDataList) {
                            markerDataList.filter { !it.useExact }
                        }

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val userLoc = (uiState as? EventListUiState.Success)?.currentUserLocation
                            
                            // 📍 Initial Camera Centering Strategy (Rule 2)
                            LaunchedEffect(userLoc, profileUiState.profile, hasCenteredCamera) {
                                if (hasCenteredCamera) return@LaunchedEffect
                                
                                val profile = profileUiState.profile
                                val targetLat = userLoc?.latitude ?: profile?.latitude
                                val targetLng = userLoc?.longitude ?: profile?.longitude
                                
                                if (targetLat != null && targetLng != null) {
                                    cameraPositionState.animate(
                                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                            com.google.android.gms.maps.model.LatLng(targetLat, targetLng),
                                            13f
                                        )
                                    )
                                    hasCenteredCamera = true
                                }
                            }

                            // Optimización 2: Memorizar propiedades para evitar flickering y re-configuración del mapa
                            val mapProperties = remember(hasLocationPermission) {
                                com.google.maps.android.compose.MapProperties(
                                    isMyLocationEnabled = hasLocationPermission
                                )
                            }
                            val mapUiSettings = remember {
                                com.google.maps.android.compose.MapUiSettings(
                                    myLocationButtonEnabled = true,
                                    zoomControlsEnabled = false,
                                    compassEnabled = true
                                )
                            }

                            com.google.maps.android.compose.GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                properties = mapProperties,
                                uiSettings = mapUiSettings,
                                onMapClick = { selectedMapEvent = null }
                            ) {
                                // 📍 DRAW RADIUS CIRCLE (Only if location is available)
                                val userLocForCircle = (uiState as? EventListUiState.Success)?.currentUserLocation
                                if (userLocForCircle != null) {
                                    com.google.maps.android.compose.Circle(
                                        center = com.google.android.gms.maps.model.LatLng(
                                            userLocForCircle.latitude,
                                            userLocForCircle.longitude
                                        ),
                                        radius = searchRadiusKm * 1000.0,
                                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        strokeWidth = 3f
                                    )
                                }
                                
                                // 🌐 CÍRCULOS DE APROXIMACIÓN (Para eventos privados sin acceso)
                                circleMarkerData.forEach { data ->
                                    val color = androidx.compose.ui.graphics.Color.hsv(data.hue, 0.5f, 0.9f)
                                    com.google.maps.android.compose.Circle(
                                        center = data.markerPosition,
                                        radius = 800.0,
                                        fillColor = color.copy(alpha = 0.15f),
                                        strokeColor = color.copy(alpha = 0.4f),
                                        strokeWidth = 2f
                                    )
                                }

                                Clustering(
                                    items = markerDataList,
                                    onClusterItemClick = { data ->
                                        selectedMapEvent = data.event
                                        true
                                    },
                                    clusterContent = { cluster ->
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.secondary
                                                        )
                                                    ),
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                                .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                                .graphicsLayer {
                                                    shadowElevation = 6.dp.toPx()
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                    clip = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cluster.size.toString(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    },
                                    clusterItemContent = { data ->
                                        if (data.useExact) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        color = androidx.compose.ui.graphics.Color.hsv(data.hue, 0.7f, 0.9f).copy(alpha = 0.9f),
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                    )
                                                    .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                                    .graphicsLayer {
                                                        shadowElevation = 4.dp.toPx()
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                        clip = true
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    data.event.eventType.emoji,
                                                    fontSize = 18.sp,
                                                    modifier = Modifier.padding(bottom = 2.dp)
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        color = androidx.compose.ui.graphics.Color.hsv(data.hue, 0.4f, 0.9f).copy(alpha = 0.4f),
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                    )
                                                    .border(1.dp, Color.White.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    data.event.eventType.emoji,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.alpha(0.6f)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            
                            // 🛰️ Rule 2c: NO LOCATION AVAILABLE STATE (UX Overlay)
                            val isLocationMissing = !hasLocationPermission && profileUiState.profile?.latitude == null
                            if (isLocationMissing) {
                                com.eventos.banana.ui.components.LocationPromptCard(
                                    onEnableGps = {
                                        permissionLauncher.launch(arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        ))
                                    },
                                    onGoToProfile = onProfileClick
                                )
                            }

                            // 📍 CHIP DE RADIO (esquina superior derecha del mapa)
                            val userLocForChip = (uiState as? EventListUiState.Success)?.currentUserLocation
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                onClick = { showRadiusDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (userLocForChip != null) "${searchRadiusKm}km" else "Global",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Ajustar radio",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // 🎨 LEYENDA DEL MAPA (esquina superior izquierda)
                            var isLegendExpanded by remember { mutableStateOf(false) }

                            com.eventos.banana.ui.components.MapLegend(
                                selectedCategory = selectedCategory,
                                onCategorySelected = { eventListViewModel.selectCategory(it) },
                                modifier = Modifier.align(Alignment.TopStart)
                            )

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
                                        isCreatorIdentityVerified = creatorProfile?.identityVerified ?: false,
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
                } // when(uiState).Success
            } // when(uiState)
        } // Column modifiers
    } // Box closing

    // Muestra la guía justo cuando el feed tiene datos (o después de 2 segundos)
    if (!hasSeenHomeGuide.value) {
        HomeGuideOverlay(
            onDismiss = {
                hasSeenHomeGuide.value = true
                sharedPrefs.edit().putBoolean("home_guide_seen", true).apply()
            }
        )
    }
} // Scaffold
} // Fin HomeScreen
}

// Fin de archivo

