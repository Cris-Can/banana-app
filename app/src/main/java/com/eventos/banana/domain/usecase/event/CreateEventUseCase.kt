package com.eventos.banana.domain.usecase.event

import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.domain.model.Event
import com.eventos.banana.util.AppConstants
import com.eventos.banana.util.GeohashUtils
import javax.inject.Inject
import kotlin.random.Random

/**
 * UseCase para orquestar la creación de un nuevo evento.
 * Maneja validación de suscripción, geohashes y estadísticas.
 */
class CreateEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    suspend operator fun invoke(event: Event, imageBytes: ByteArray? = null, searchRadiusKm: Int = AppConstants.DEFAULT_SEARCH_RADIUS_KM): Result<Unit> {
        return try {
            // 1. Validar Límites (Suscripción)
            val canCreate = subscriptionRepository.canCreateEvent(event.creatorId)
            if (canCreate.isFailure || !canCreate.getOrDefault(false)) {
                return Result.failure(Exception("LIMIT_REACHED"))
            }

            // 1.6 Validar +18 (identityVerified)
            if (event.isAdultContent) {
                val creatorProfile = userRepository.getUserProfile(event.creatorId)
                if (creatorProfile == null || !creatorProfile.identityVerified) {
                    return Result.failure(Exception("Debes verificar tu identidad para crear eventos +18"))
                }
            }

            // 1.5 Validar Ubicación
            val exactLat = event.exactLatitude ?: event.latitude
            val exactLng = event.exactLongitude ?: event.longitude

            if (exactLat == null || exactLng == null) {
                return Result.failure(Exception("LOCATION_REQUIRED"))
            }

            // 2. Ofuscación de Ubicación (Seguridad Física)
            
            // Generate Fuzzed coordinates (approx 2.5km - 3.5km offset)
            // 1 degree lat/lng ~= 111km. 0.03 ~= 3.3km
            val eventWithFuzzedLocation = if (exactLat != null && exactLng != null) {
                // Si el evento es PÚBLICO, no ofuscamos en los campos principales (o usamos los mismos exactos)
                // Pero por diseño, los campos 'latitude'/'longitude' son los que se ven en el mapa feed
                // y 'exactLatitude'/'exactLongitude' son los reales.
                
                if (event.isPublic) {
                    event.copy(
                        exactLatitude = exactLat,
                        exactLongitude = exactLng,
                        latitude = exactLat,
                        longitude = exactLng,
                        exactAddress = event.address,
                        address = event.address
                    )
                } else {
                    // Private event -> APPLY FUZZING to public fields
                    val latOffset = (Random.nextDouble(-0.03, 0.03))
                    val lngOffset = (Random.nextDouble(-0.03, 0.03))
                    
                    val fuzzedLat = exactLat + latOffset
                    val fuzzedLng = exactLng + lngOffset
                    
                    event.copy(
                        exactLatitude = exactLat,
                        exactLongitude = exactLng,
                        latitude = fuzzedLat,  // PUBLIC COORD (Fuzzed)
                        longitude = fuzzedLng,  // PUBLIC COORD (Fuzzed)
                        exactAddress = event.address,
                        address = if (event.commune.isNotBlank()) event.commune else "Ubicación aproximada" // PUBLIC ADDRESS (Hidden/City only)
                    )
                }
            } else {
                event
            }

            // 3. Generar Geohash (Precisión 9 para almacenamiento)
            val eventWithGeohash = if (eventWithFuzzedLocation.latitude != null && eventWithFuzzedLocation.longitude != null) {
                val precision = GeohashUtils.getPrecisionForRadius(searchRadiusKm)
                val hash = GeohashUtils.encode(eventWithFuzzedLocation.latitude, eventWithFuzzedLocation.longitude, precision)
                android.util.Log.d("BANANA_DEBUG", "Creando evento: lat=${eventWithFuzzedLocation.latitude}, lng=${eventWithFuzzedLocation.longitude}, precision=$precision, geohash=$hash")
                eventWithFuzzedLocation.copy(geohash = hash)
            } else {
                android.util.Log.e("BANANA_DEBUG", "Creando evento SIN coordenadas")
                eventWithFuzzedLocation
            }

            // 4. Crear el evento a través del repositorio
            val result = eventRepository.createEvent(eventWithGeohash, imageBytes)
            
            if (result.isSuccess) {
                // 4. Incrementar estadísticas de vida post-creación exitosa 
                // (eventsCreatedInCycle se incrementa automáticamente vía Cloud Functions)
                userRepository.incrementEventsCreatedLifetime(event.creatorId)
                Result.success(Unit)
            } else {
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("CreateEventUseCase", "Error orchestrating event creation", e)
            Result.failure(e)
        }
    }
}
