package com.eventos.banana.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.ui.event.EventListViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    currentUserId: String, // ➕ Pass ID explicitly
    isIdentityVerified: Boolean = false,
    viewModel: EventListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val searchRadiusKm by viewModel.searchRadiusKm.collectAsState()

    // 📍 Camera State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-33.4489, -70.6693), 12f) // Santiago Default
    }

    // 📍 Get Location one-off
    LaunchedEffect(Unit) {
        val helper = com.eventos.banana.util.LocationHelper(context)
        val location = helper.getCurrentLocation()
        if (location != null) {
            viewModel.updateLocation(location.latitude, location.longitude)
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude), 
                    13f
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.map_events_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.eventos.banana.R.string.common_back_nav))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding() // Transparent Overlay style
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            val events = (uiState as? EventListUiState.Success)?.events ?: emptyList()

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = true
                ),
                properties = MapProperties(
                    isMyLocationEnabled = true
                )
            ) {
                // 🔴 Draw Circles for Radius
                val userLocation = (uiState as? EventListUiState.Success)?.currentUserLocation
                if (userLocation != null) {
                    Circle(
                        center = LatLng(userLocation.latitude, userLocation.longitude),
                        radius = searchRadiusKm * 1000.0, // Convert km to meters
                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        strokeWidth = 2f,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }

                // 📍 Event Markers
                events.forEach { event ->
                    if (event.latitude != null && event.longitude != null) {
                        // 🔞 Ocultar +18 si el usuario no está verificado
                        if (event.isAdultContent && !isIdentityVerified) return@forEach
                        // 🔒 PRIVACY LOGIC:
                        // Show exact location if:
                        // 1. User is Creator
                        // 2. User is Approved Participant
                        // 3. Event is PUBLIC (Open)
                         // currentUserId is passed as param now
                        val isExactVisible = event.isPublic || 
                                             event.creatorId == currentUserId || 
                                             event.approvedParticipants.contains(currentUserId)

                        val displayLatLng = if (isExactVisible) {
                            LatLng(event.latitude, event.longitude)
                        } else {
                            // 🎲 Fuzzing: Add stable random offset based on Event ID
                            // Offset approx +/- 200-500m to hide exact house
                            val seed = event.id.hashCode()
                            val offsetLat = (seed % 100) / 10000.0 // +/- 0.01 deg max
                            val offsetLng = ((seed / 100) % 100) / 10000.0 
                            LatLng(event.latitude + offsetLat, event.longitude + offsetLng)
                        }

                        Marker(
                            state = MarkerState(position = displayLatLng),
                            title = event.title,
                            snippet = if (isExactVisible) "${event.eventType.emoji} ${event.commune}" else stringResource(com.eventos.banana.R.string.map_approximate_zone),
                            onClick = {
                                onEventClick(event.id)
                                true
                            },
                             icon = if (!isExactVisible) {
                                // Optional: Use a generic circle or different color for approximate?
                                // For now keep default marker but maybe semi-transparent or different hue if possible
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_VIOLET
                                )
                            } else {
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
                                )
                            }
                        )
                    }
                }
            }
            
            // 🏷️ Radius Indicator
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp), // Below TopBar
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Column(
                   modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                   horizontalAlignment = Alignment.CenterHorizontally
               ) {
                   Text(
                       stringResource(com.eventos.banana.R.string.map_radius_label, searchRadiusKm),
                       style = MaterialTheme.typography.labelLarge,
                       fontWeight = FontWeight.Bold
                   )
                   Slider(
                       value = searchRadiusKm.toFloat(),
                       onValueChange = { viewModel.updateRadius(it.toInt()) },
                       valueRange = 1f..100f,
                       steps = 9,
                       modifier = Modifier.width(200.dp)
                   )
               }
            }
            
            // ⚠️ Loading Indicator
            if (uiState is EventListUiState.Loading) {
                 CircularProgressIndicator(
                     modifier = Modifier.align(Alignment.Center)
                 )
            }
        }
    }
}
