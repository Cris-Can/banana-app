package com.eventos.banana.ui.maps

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLocationPickerScreen(
    initialLatitude: Double? = -33.4489,  // Santiago default, nullable
    initialLongitude: Double? = -70.6693,
    onLocationSelected: (latitude: Double, longitude: Double, address: String) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedPosition by remember(initialLatitude, initialLongitude) { 
        mutableStateOf(LatLng(initialLatitude ?: -33.4489, initialLongitude ?: -70.6693)) 
    }
    var address by remember { mutableStateOf("") }
    
    // Reverse Geocoding
    LaunchedEffect(selectedPosition) {
        kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(selectedPosition.latitude, selectedPosition.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    address = addresses[0].getAddressLine(0) ?: ""
                }
            } catch (e: Exception) {
                // Ignore geocoding errors
            }
        }
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedPosition, 15f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar Ubicación Exacta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onLocationSelected(
                                selectedPosition.latitude,
                                selectedPosition.longitude,
                                address
                            )
                        }
                    ) {
                        Icon(Icons.Default.Check, "Confirmar")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mapa
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    selectedPosition = latLng
                }
            ) {
                Marker(
                    state = MarkerState(position = selectedPosition),
                    title = "Ubicación del evento"
                )
            }

            // Info card en la parte inferior
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Arrastra el marcador o toca en el mapa",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Lat: ${String.format("%.6f", selectedPosition.latitude)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Lon: ${String.format("%.6f", selectedPosition.longitude)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}
