package com.eventos.banana.ui.maps

import android.location.Address
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLocationPickerScreen(
    initialLatitude: Double? = -33.4489,  // Santiago default, nullable
    initialLongitude: Double? = -70.6693,
    onLocationSelected: (latitude: Double, longitude: Double, address: String, commune: String, region: String, country: String) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedPosition by remember(initialLatitude, initialLongitude) { 
        mutableStateOf(LatLng(initialLatitude ?: -33.4489, initialLongitude ?: -70.6693)) 
    }
    var address by remember { mutableStateOf("") }
    var commune by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    var selectedSearchResultIndex by remember { mutableIntStateOf(-1) }
    
    // Reverse Geocoding
    LaunchedEffect(selectedPosition) {
        try {
            val addresses = withContext(Dispatchers.IO) {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(selectedPosition.latitude, selectedPosition.longitude, 1)
            }
            if (!addresses.isNullOrEmpty()) {
                address = addresses[0].getAddressLine(0) ?: ""
                region = addresses[0].adminArea ?: ""
                commune = addresses[0].subLocality ?: addresses[0].locality ?: ""
                country = addresses[0].countryName ?: ""
            }
        } catch (e: Exception) {
            // Ignore geocoding errors
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 3) {
            searchResults = emptyList()
            showDropdown = false
            return@LaunchedEffect
        }
        delay(300)
        isSearching = true
        showDropdown = true
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale("es", "CL"))
            @Suppress("DEPRECATION")
            val results = withContext(Dispatchers.IO) {
                geocoder.getFromLocationName(searchQuery, 10)
            }
            searchResults = results ?: emptyList()
            if (searchResults.isEmpty()) showDropdown = false
        } catch (e: Exception) {
            searchResults = emptyList()
            showDropdown = false
        }
        isSearching = false
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedPosition, 15f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.map_select_exact_location)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.eventos.banana.R.string.common_back_nav))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onLocationSelected(
                                selectedPosition.latitude,
                                selectedPosition.longitude,
                                address,
                                commune,
                                region,
                                country
                            )
                        }
                    ) {
                        Icon(Icons.Default.Check, stringResource(com.eventos.banana.R.string.common_confirm_nav))
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
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    selectedPosition = latLng
                    showDropdown = false
                }
            ) {
                Marker(
                    state = MarkerState(position = selectedPosition),
                    title = stringResource(com.eventos.banana.R.string.map_event_location)
                )
            }

            // Search bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(8.dp)),
                    placeholder = { Text("Buscar lugar...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Buscar") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                showDropdown = false
                            }) {
                                Icon(Icons.Default.Clear, "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search results dropdown
                if (showDropdown && searchResults.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn {
                            itemsIndexed(searchResults, key = { index, _ -> "result_$index" }) { index, address ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPosition = LatLng(address.latitude, address.longitude)
                                            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                                LatLng(address.latitude, address.longitude), 15f
                                            )
                                            searchQuery = address.getAddressLine(0) ?: ""
                                            showDropdown = false
                                            searchResults = emptyList()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = address.getAddressLine(0) ?: "Ubicación",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                }
                                if (index < searchResults.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
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
                        stringResource(com.eventos.banana.R.string.map_drag_or_tap),
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
                        label = { Text(stringResource(com.eventos.banana.R.string.map_address_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

