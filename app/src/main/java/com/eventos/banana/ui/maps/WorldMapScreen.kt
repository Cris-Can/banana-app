package com.eventos.banana.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventos.banana.domain.model.EventListUiState
import com.eventos.banana.viewmodel.EventListViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: EventListViewModel = viewModel()
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
                title = { Text("Mapa de Eventos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
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
                // 🔴 Draw Circles for Radius?
                // Optional: Draw a circle around user location representing searchRadiusKm

                // 📍 Event Markers
                events.forEach { event ->
                    if (event.latitude != null && event.longitude != null) {
                        Marker(
                            state = MarkerState(position = LatLng(event.latitude, event.longitude)),
                            title = event.title,
                            snippet = "${event.eventType.emoji} ${event.commune}",
                            onClick = {
                                onEventClick(event.id)
                                true
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
               Row(
                   modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                   verticalAlignment = Alignment.CenterVertically
               ) {
                   Text(
                       "Radio: $searchRadiusKm km",
                       style = MaterialTheme.typography.labelLarge,
                       fontWeight = FontWeight.Bold
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
