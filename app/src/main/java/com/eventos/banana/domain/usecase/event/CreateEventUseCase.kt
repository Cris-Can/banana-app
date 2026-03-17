package com.eventos.banana.domain.usecase.event

import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.domain.model.Event
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
    suspend operator fun invoke(event: Event, imageBytes: ByteArray? = null): Result<Unit> {
        return try {
            // 1. Validar Límites (Suscripción)
            val canCreate = subscriptionRepository.canCreateEvent(event.creatorId)
            if (canCreate.isFailure || !canCreate.getOrDefault(false)) {
                return Result.failure(Exception("LIMIT_REACHED"))
            }

            // 2. Ofuscación de Ubicación (Seguridad Física)
            val exactLat = event.exactLatitude ?: event.latitude
            val exactLng = event.exactLongitude ?: event.longitude
            
            // Generate Fuzzed coordinates (approx 300m - 800m offset)
            val eventWithFuzzedLocation = if (exactLat != null && exactLng != null) {
                // 1 degree lat/lng ~= 111km at equator. 0.005 ~= 550m
                val latOffset = (Random.nextDouble(-0.007, 0.007))
                val lngOffset = (Random.nextDouble(-0.007, 0.007))
                
                val fuzzedLat = exactLat + latOffset
                val fuzzedLng = exactLng + lngOffset
                
                event.copy(
                    exactLatitude = exactLat,
                    exactLongitude = exactLng,
                    latitude = fuzzedLat,  // PUBLIC COORD
                    longitude = fuzzedLng  // PUBLIC COORD
                )
            } else {
                event
            }

            // 3. Generar Geohash (Usando las coordenadas públicas/ofuscadas)
            val eventWithGeohash = if (eventWithFuzzedLocation.latitude != null && eventWithFuzzedLocation.longitude != null) {
                val hash = GeohashUtils.encode(eventWithFuzzedLocation.latitude, eventWithFuzzedLocation.longitude, 9)
                eventWithFuzzedLocation.copy(geohash = hash)
            } else {
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
