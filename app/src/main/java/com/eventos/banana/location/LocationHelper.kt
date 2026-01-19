package com.eventos.banana.location

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

import com.eventos.banana.data.location.ChileLocationProvider

data class LocationResult(
    val region: String?,
    val commune: String?,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class LocationHelper(private val context: Context) {

    private val fusedClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getRegionAndCommune(): LocationResult =
        suspendCancellableCoroutine { cont ->

            fusedClient.lastLocation
                .addOnSuccessListener { location ->

                    if (location == null) {
                        cont.resume(LocationResult(null, null))
                        return@addOnSuccessListener
                    }

                    try {
                        val geocoder = Geocoder(context, Locale("es", "CL"))
                        val addresses =
                            geocoder.getFromLocation(
                                location.latitude,
                                location.longitude,
                                1
                            )

                        val address = addresses?.firstOrNull()
                        val detectedLocality = address?.locality
                        val detectedAdminArea = address?.adminArea
                        val fullAddressLine = address?.getAddressLine(0) ?: ""

                        // Cargar todas las comunas válidas
                        val regionsMap = ChileLocationProvider.getRegionsWithCommunes(context)
                        val allCommunes = regionsMap.values.flatten()

                        // 1. Intentar match exacto con locality
                        var finalCommune = allCommunes.find { it.equals(detectedLocality, ignoreCase = true) }

                        // 2. Si falla, buscar si alguna comuna válida está contenida en la dirección completa
                        // (Ej: "Maipo" -> "Camino Buin 123, Buin" -> Match "Buin")
                        if (finalCommune == null) {
                            finalCommune = allCommunes.find { validCommune ->
                                fullAddressLine.contains(validCommune, ignoreCase = true)
                            }
                        }

                        // 3. Fallback: Usar subAdminArea si coincide
                        if (finalCommune == null) {
                            finalCommune = allCommunes.find { it.equals(address?.subAdminArea, ignoreCase = true) }
                        }
                        
                        // 4. Fallback final: Devolver lo que traiga el geocoder (Aunque no sea válido, mejor que nada)
                        if (finalCommune == null) {
                            finalCommune = detectedLocality
                        }

                        // Intentar corregir Región si tenemos la comuna identificada
                        var finalRegion = detectedAdminArea
                        if (finalCommune != null) {
                            // Buscar a qué región pertenece esta comuna
                            val regionEntry = regionsMap.entries.find { it.value.contains(finalCommune) }
                            if (regionEntry != null) {
                                finalRegion = regionEntry.key
                            }
                        }

                        cont.resume(
                            LocationResult(
                                region = finalRegion,
                                commune = finalCommune,
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        )

                    } catch (e: Exception) {
                        cont.resume(LocationResult(null, null))
                    }
                }
                .addOnFailureListener {
                    cont.resume(LocationResult(null, null))
                }
        }
}
