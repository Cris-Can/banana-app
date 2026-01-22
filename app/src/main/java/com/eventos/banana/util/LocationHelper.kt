package com.eventos.banana.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Helper para detección automática de ubicación usando GPS
 */
class LocationHelper(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    companion object {
        private const val TAG = "LocationHelper"
        
        /**
         * Verifica si los permisos de ubicación están concedidos
         */
        fun hasLocationPermissions(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Obtiene la ubicación actual del usuario
     * Requiere que los permisos estén concedidos
     */
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermissions(context)) {
            Log.w(TAG, "Location permissions not granted")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                object : CancellationToken() {
                    override fun onCanceledRequested(p0: OnTokenCanceledListener) = 
                        CancellationTokenSource().token
                    
                    override fun isCancellationRequested() = false
                }
            ).addOnSuccessListener { location: Location? ->
                Log.d(TAG, "Location obtained: $location")
                continuation.resume(location)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                continuation.resume(null)
            }
            
            continuation.invokeOnCancellation {
                Log.d(TAG, "Location request cancelled")
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            continuation.resume(null)
        }
    }
    
    /**
     * Obtiene el nombre de la comuna basado en coordenadas GPS
     * Usa Geocoding reverso
     */
    suspend fun getCommuneFromLocation(location: Location): String? {
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale("es", "CL"))
            
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )
            
            if (!addresses.isNullOrEmpty()) {
                // Iterar sobre las direcciones encontradas para buscar una coincidencia válida
                for (address in addresses) {
                    Log.d(TAG, "Checking address: $address")
                    
                    // Probamos varios campos en orden de probabilidad
                    val candidates = listOfNotNull(
                        address.locality,       // Comuna/Ciudad
                        address.subAdminArea,   // Comuna/Provincia
                        address.adminArea       // Región (menos probable pero posible)
                    )
                    
                    for (candidate in candidates) {
                        Log.d(TAG, "Candidate: $candidate")
                        val match = com.eventos.banana.data.ChileCommunesList.findClosest(candidate)
                        if (match != null) {
                            Log.d(TAG, "Match found: $match")
                            return match
                        }
                    }
                }
                
                // Fallback: Buscar en la dirección completa (line[0])
                // Útil cuando locality es un barrio (ej: "Maipo") y la comuna está en el string (ej: "Buin")
                val fullAddress = addresses.firstOrNull()?.getAddressLine(0)
                if (!fullAddress.isNullOrBlank()) {
                    Log.d(TAG, "Trying full address match: $fullAddress")
                    val match = com.eventos.banana.data.ChileCommunesList.findCommuneInText(fullAddress)
                    if (match != null) {
                        Log.d(TAG, "Match found in full address: $match")
                        return match
                    }
                }
                
                Log.w(TAG, "No valid commune match found in addresses")
                null
            } else {
                Log.w(TAG, "No addresses found for location")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error geocoding location", e)
            null
        }
    }
    
    /**
     * Detección completa: GPS + Geocoding
     * Retorna el nombre de la comuna detectada
     */
    suspend fun detectCurrentCommune(): String? {
        val location = getCurrentLocation() ?: return null
        return getCommuneFromLocation(location)
    }

    data class LocationDetectionResult(
        val commune: String,
        val region: String,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * Detección completa con coordenadas y región
     */
    suspend fun detectLocationFull(): LocationDetectionResult? {
        val location = getCurrentLocation() ?: return null
        val commune = getCommuneFromLocation(location) ?: return null
        val region = com.eventos.banana.data.ChileCommunesList.getRegionForCommune(commune)
        
        return LocationDetectionResult(
            commune = commune,
            region = region,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }
}
