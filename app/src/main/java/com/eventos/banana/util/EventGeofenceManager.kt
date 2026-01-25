package com.eventos.banana.util

import android.content.Context
import android.location.Location
import com.eventos.banana.domain.model.Event
import kotlin.math.roundToInt
import android.util.Log

/**
 * Manager para verificar si el usuario está físicamente en el evento
 * Usa LocationHelper para obtener coordenadas actuales
 */
class EventGeofenceManager(private val context: Context) {

    private val locationHelper = LocationHelper(context)
    
    companion object {
        private const val TAG = "EventGeofenceManager"
        private const val MAX_DISTANCE_METERS = 100 // Radio de 100 metros
    }

    /**
     * Verifica si el usuario está dentro del radio del evento
     */
    suspend fun isUserAtEvent(event: Event): Boolean {
        val distance = getDistanceToEvent(event) ?: return false
        return distance <= MAX_DISTANCE_METERS
    }

    /**
     * Calcula la distancia en metros entre el usuario y el evento
     * Retorna null si no se puede obtener la ubicación o el evento no tiene coordenadas
     */
    suspend fun getDistanceToEvent(event: Event): Int? {
        // Validación preliminar: coordenadas del evento
        val eventLat = event.exactLatitude
        val eventLng = event.exactLongitude
        
        if (eventLat == null || eventLng == null) {
            Log.w(TAG, "Event does not have exact location")
            return null
        }

        // Obtener ubicación actual
        val userLocation = locationHelper.getCurrentLocation()
        if (userLocation == null) {
            Log.w(TAG, "Could not get user location")
            return null
        }

        // Calcular distancia
        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude,
            userLocation.longitude,
            eventLat,
            eventLng,
            results
        )

        val distance = results[0].roundToInt()
        Log.d(TAG, "Distance calculated: $distance meters")
        return distance
    }
}
