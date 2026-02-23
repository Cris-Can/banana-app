package com.eventos.banana.domain.usecase.event

import com.eventos.banana.data.repository.EventRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.data.repository.SubscriptionRepository
import com.eventos.banana.domain.model.Event
import com.eventos.banana.util.GeohashUtils
import javax.inject.Inject

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

            // 2. Generar Geohash
            val lat = event.exactLatitude ?: event.latitude
            val lng = event.exactLongitude ?: event.longitude
            
            val eventWithGeohash = if (lat != null && lng != null) {
                val hash = GeohashUtils.encode(lat, lng, 9)
                event.copy(geohash = hash)
            } else {
                event
            }

            // 3. Crear el evento a través del repositorio
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
