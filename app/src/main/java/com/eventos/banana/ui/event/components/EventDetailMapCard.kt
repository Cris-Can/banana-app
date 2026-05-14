package com.eventos.banana.ui.event.components


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun EventDetailMapCard(
    event: Event,
    isCreator: Boolean,
    isApproved: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (event.exactLatitude != null && event.exactLongitude != null) {
        val eventLocation = LatLng(event.exactLatitude, event.exactLongitude)
        
        // Determinar si puede ver el mapa interactivo (creador o aprobado en evento privado, o siempre en evento público)
        val canSeeMap = isCreator || isApproved || event.isPublic

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Título e indicación
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Ubicación",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (canSeeMap) "Ubicación Exacta" else "Ubicación Aproximada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (canSeeMap) event.exactAddress ?: "Dirección oculta" else event.address ?: "Dirección aproximada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // El Mapa (Interactive or Blurred)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    if (canSeeMap) {
                        // Mapa Interactivo Real
                        val cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(eventLocation, 15f)
                        }
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                mapType = MapType.NORMAL,
                                isMyLocationEnabled = false
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                scrollGesturesEnabled = true,
                                zoomGesturesEnabled = true
                            )
                        ) {
                            Marker(
                                state = MarkerState(position = eventLocation),
                                title = event.title,
                                snippet = if (canSeeMap) event.exactAddress else event.address
                            )
                        }
                    } else {
                        // --- STATIC MAP FOR NON-APPROVED USERS ---
                        val cameraPositionState = rememberCameraPositionState {
                            // Zoom out to obscure exact location (radius)
                            position = CameraPosition.fromLatLngZoom(eventLocation, 13f)
                        }
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                mapType = MapType.NORMAL,
                                isMyLocationEnabled = false
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false,
                                compassEnabled = false,
                                mapToolbarEnabled = false
                            )
                        ) {
                             // Circle instead of marker for privacy
                             Circle(
                                center = eventLocation,
                                radius = 800.0, // 800 meters radius
                                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                strokeColor = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2f
                             )
                        }
                        
                        // Overlay to disable clicking and indicate restriction
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.1f)) // Slight dim
                                .clickable { 
                                    Toast.makeText(context, "Debes unirte para ver la ubicación exacta", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Optional: Put a lock icon or text in the center
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Radio aproximado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
