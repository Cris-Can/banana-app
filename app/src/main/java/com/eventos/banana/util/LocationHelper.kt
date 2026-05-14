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

        /**
         * Verifica si los servicios de ubicación (GPS) están encendidos en el dispositivo
         */
        fun isLocationEnabled(context: Context): Boolean {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            return try {
                locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) {
                false
            }
        }

        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
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
            val geocoder = Geocoder(context, Locale.getDefault())
            
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                Log.d(TAG, "Address found: $address")
                
                // Retornar la localidad (ciudad/comuna) directamente
                return address.locality ?: address.subAdminArea ?: address.adminArea
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
     * Retorna el nombre de la localidad detectada
     */
    suspend fun detectCurrentCommune(): String? {
        val location = getCurrentLocation() ?: return null
        return getCommuneFromLocation(location)
    }

    data class LocationDetectionResult(
        val commune: String,
        val region: String,
        val country: String,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * Detección completa a partir de coordenadas específicas
     */
    suspend fun detectFromCoordinates(lat: Double, lng: Double): LocationDetectionResult? {
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                LocationDetectionResult(
                    commune = addr.locality ?: addr.subAdminArea ?: "Desconocida",
                    region = addr.adminArea ?: "Desconocida",
                    country = addr.countryName ?: "Desconocido",
                    latitude = lat,
                    longitude = lng
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectFromCoordinates", e)
            null
        }
    }

    /**
     * Detección completa con coordenadas y región (Global)
     */
    suspend fun detectLocationFull(): LocationDetectionResult? {
        val location = getCurrentLocation() ?: return null
        return detectFromCoordinates(location.latitude, location.longitude)
    }

    /**
     * Obtiene el cliente de Google Places de forma compartida
     */
    fun getPlacesClient(): com.google.android.libraries.places.api.net.PlacesClient {
        return com.google.android.libraries.places.api.Places.createClient(context)
    }
}
