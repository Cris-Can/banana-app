package com.eventos.banana.ui.event.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.ui.event.CheckInState
import com.eventos.banana.util.EventGeofenceManager
import com.eventos.banana.util.LocationHelper
import kotlinx.coroutines.launch

@Composable
fun EventDetailCheckInCard(
    event: Event,
    hasAttended: Boolean,
    isCreator: Boolean,
    isApproved: Boolean,
    checkInState: CheckInState,
    onCheckInClick: () -> Unit,
    onRateParticipants: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geofenceManager = remember { EventGeofenceManager(context) }

    Column(modifier = modifier) {
        // ========== ENCUENTROS & PUNTUACIÓN ==========
        if (event.status == EventStatus.OPEN || event.status == EventStatus.CLOSED) {
            val canValidate = isCreator || isApproved
            if (canValidate) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📱 Encuentros & Puntuación",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (hasAttended || isCreator) {
                            // ✅ COMPACT SUCCESS UI (Static - Visual Confirmation)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "✅ Check-in Realizado",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            // 🔘 ORIGINAL BUTTONS (GPS Check-In)
                            Text(
                                "Para puntuar a otros asistentes, primero debes confirmar que estuviste ahí.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(12.dp))

                            var isVerifyingLocation by remember { mutableStateOf(false) }
                            var currentDistance by remember { mutableStateOf<Int?>(null) }

                            // Auto-check distance on load
                            LaunchedEffect(Unit) {
                                if (LocationHelper.hasLocationPermissions(context) &&
                                    LocationHelper.isLocationEnabled(context)
                                ) {
                                    currentDistance = geofenceManager.getDistanceToEvent(event)
                                }
                            }

                            // Distance Indicator
                            if (currentDistance != null) {
                                val isCloseEnough = currentDistance!! <= 100
                                Text(
                                    text = "📍 Distancia actual: $currentDistance m",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isCloseEnough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    // Check Permissions
                                    if (!LocationHelper.hasLocationPermissions(context)) {
                                        Toast.makeText(context, "⚠️ Permiso de ubicación requerido", Toast.LENGTH_LONG).show()
                                        return@OutlinedButton
                                    }
                                    // Time Validation
                                    val now = System.currentTimeMillis()
                                    val oneHour = 3600000L
                                    if (event.startAt > 0 && now < (event.startAt - oneHour)) {
                                        Toast.makeText(context, "🕒 Muy temprano (espera al inicio)", Toast.LENGTH_LONG).show()
                                        return@OutlinedButton
                                    }
                                    if (event.endAt > 0 && now > (event.endAt + oneHour)) {
                                        Toast.makeText(context, "🕒 El evento ya finalizó", Toast.LENGTH_LONG).show()
                                        return@OutlinedButton
                                    }
                                    // GPS Enabled
                                    if (!LocationHelper.isLocationEnabled(context)) {
                                        Toast.makeText(context, "⚠️ Enciende tu GPS", Toast.LENGTH_LONG).show()
                                        return@OutlinedButton
                                    }

                                    isVerifyingLocation = true
                                    coroutineScope.launch {
                                        try {
                                            // Update distance
                                            currentDistance = geofenceManager.getDistanceToEvent(event)
                                            val isAtEvent = geofenceManager.isUserAtEvent(event)

                                            if (isAtEvent) {
                                                onCheckInClick()
                                            } else {
                                                Toast.makeText(context, "❌ Debes estar a 100m del evento", Toast.LENGTH_LONG).show()
                                            }
                                        } finally {
                                            isVerifyingLocation = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                enabled = !isVerifyingLocation && checkInState !is CheckInState.Loading
                            ) {
                                if (isVerifyingLocation || checkInState is CheckInState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(com.eventos.banana.R.string.event_detail_verifying))
                                } else {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(com.eventos.banana.R.string.event_detail_gps_checkin))
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // ========== CALIFICAR PARTICIPANTES ==========
        val now = System.currentTimeMillis()
        val eventEnded = event.endAt < now || event.status == EventStatus.CLOSED
        val canRate = (isCreator || isApproved) && eventEnded
        val ratingDeadline = event.ratingDeadline ?: (event.endAt + com.eventos.banana.util.AppConstants.RATING_DEADLINE_MS) // 5 días después
        val withinRatingWindow = now <= ratingDeadline

        if (canRate && withinRatingWindow) {
            Spacer(Modifier.height(8.dp))

            // 🔒 VALIDACIÓN DE ASISTENCIA (Advertencia, no bloqueo)
            if (!isCreator && !hasAttended) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(com.eventos.banana.R.string.event_detail_unverified_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(com.eventos.banana.R.string.event_detail_rate_requirement), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(com.eventos.banana.R.string.event_detail_rate_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))

                    val daysRemaining = ((ratingDeadline - now) / 86400000L).toInt()
                    Text(
                        stringResource(com.eventos.banana.R.string.event_detail_days_remaining, daysRemaining, if (daysRemaining == 1) stringResource(com.eventos.banana.R.string.event_detail_day) else stringResource(com.eventos.banana.R.string.event_detail_days)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onRateParticipants(event) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.eventos.banana.R.string.event_detail_rate_now))
                    }
                }
            }
        }
    }
}
