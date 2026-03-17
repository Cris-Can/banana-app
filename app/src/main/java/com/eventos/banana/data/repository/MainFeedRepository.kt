package com.eventos.banana.data.repository

import com.eventos.banana.data.remote.model.EventDto
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class MainFeedRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val eventsCollection = firestore.collection("events")

    /**
     * Obtiene un lote de eventos para el feed principal con soporte para paginación y filtros.
     */
    suspend fun fetchEventsBatch(
        geohashPrefix: String? = null,
        commune: String? = null,
        region: String? = null,
        limit: Long = 20,
        lastSnapshot: com.google.firebase.firestore.DocumentSnapshot? = null
    ): Result<Pair<List<Event>, com.google.firebase.firestore.DocumentSnapshot?>> {
        return try {
            var query: Query = eventsCollection

            // Filtros de ubicación
            if (!commune.isNullOrBlank()) {
                query = query.whereEqualTo("commune", commune)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            } else if (!region.isNullOrBlank()) {
                query = query.whereEqualTo("region", region)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            } else if (!geohashPrefix.isNullOrEmpty()) {
                query = query.orderBy("geohash")
                    .startAt(geohashPrefix)
                    .endAt(geohashPrefix + "~")
            } else {
                query = query.orderBy("createdAt", Query.Direction.DESCENDING)
            }

            // Paginación
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }

            val snapshot = query.limit(limit).get().await()
            val now = System.currentTimeMillis()
            
            val eventsDto = snapshot.toObjects(EventDto::class.java)
            
            // Mapeo a dominio y filtrado de eventos activos
            val filteredEvents = eventsDto.map { it.toDomain() }.filter { 
                it.status == EventStatus.OPEN && it.endAt > now 
            }.sortedWith(
                compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                    .thenByDescending { it.createdAt }
            )

            val newLastDoc = if (snapshot.documents.isNotEmpty()) snapshot.documents.last() else null
            Result.success(Pair(filteredEvents, newLastDoc))
        } catch (e: Exception) {
            android.util.Log.e("MainFeedRepository", "Error fetching events batch: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene eventos en un radio específico usando Bounding Box y Haversine.
     * @param centerLat Latitud del usuario
     * @param centerLng Longitud del usuario
     * @param radiusKm Radio en kilómetros
     * @param limit Máximo de eventos a devolver
     */
    suspend fun getEventsInRadius(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        limit: Long = 100 // Ligeramente mayor porque filtramos en cliente
    ): Result<List<Event>> {
        return try {
            // 1. Calcular Bounding Box
            // 1 grado de latitud ~= 111 km.
            val latOffset = radiusKm / 111.0
            
            // 1 grado de longitud ~= 111 km * cos(latitud).
            val lngOffset = radiusKm / (111.0 * cos(Math.toRadians(centerLat)))
            
            val minLat = centerLat - latOffset
            val maxLat = centerLat + latOffset
            // El bounding box de longitud no cruza el antimeridiano en sudamérica, 
            // pero para una versión global robusta habría que manejar ese edge case.
            val minLng = centerLng - lngOffset
            val maxLng = centerLng + lngOffset

            // 2. Consulta de Firestore (usamos latitude pública ofuscada para filtrar rápido)
            // IMPORTANTE: Firestore solo permite inecuaciones (<, >) en UN SOLO campo a la vez.
            // Por ende, filtramos la latitud en BD y la longitud en Memoria, o viceversa.
            // Optamos por filtrar latitud en BD por ser más directa.
            val snapshot = eventsCollection
                .whereGreaterThanOrEqualTo("latitude", minLat)
                .whereLessThanOrEqualTo("latitude", maxLat)
                .limit(limit)
                .get()
                .await()

            val now = System.currentTimeMillis()
            val eventsDto = snapshot.toObjects(EventDto::class.java)

            // 3. Filtrado Fino (Haversine) y Estado
            val filteredEvents = eventsDto.map { it.toDomain() }.filter { event ->
                // Solo abiertos y no expirados
                if (event.status != EventStatus.OPEN || event.endAt <= now) return@filter false
                
                // Extraer coordenadas (usar la pública)
                val eLat = event.latitude ?: return@filter false
                val eLng = event.longitude ?: return@filter false

                // Filtro rápido Bounding Box en Longitud (ya que no pudimos hacerlo en la BD)
                if (eLng < minLng || eLng > maxLng) return@filter false

                // Filtro exacto (Círculo de Haversine)
                val distance = calculateHaversineDistance(centerLat, centerLng, eLat, eLng)
                distance <= radiusKm
            }.sortedWith(
                compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                    .thenByDescending { it.createdAt }
            )

            Result.success(filteredEvents)
        } catch (e: Exception) {
            android.util.Log.e("MainFeedRepository", "Error fetching events by radius: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fórmula de Haversine para calcular distancia en KMs entre dos coordenadas
     */
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radio de la Tierra en kilómetros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Observa eventos cercanos en tiempo real (Legacy support / Realtime feed)
     */
    fun observeNearbyEvents(
        geohashPrefix: String? = null, 
        commune: String? = null, 
        region: String? = null, 
        limit: Long = 20
    ): Flow<List<Event>> = callbackFlow {
        var query: Query = eventsCollection

        if (!commune.isNullOrBlank()) {
            query = query.whereEqualTo("commune", commune)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        } else if (!region.isNullOrBlank()) {
            query = query.whereEqualTo("region", region)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        } else if (!geohashPrefix.isNullOrEmpty()) {
            val endParams = geohashPrefix + "~"
            query = query
                .orderBy("geohash")
                .startAt(geohashPrefix)
                .endAt(endParams)
        } else {
            query = query.orderBy("createdAt", Query.Direction.DESCENDING)
        }

        val listener = query.limit(limit).addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("MainFeedRepository", "Error observing events: ${error.message}", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            val now = System.currentTimeMillis()
            val events = snapshot
                ?.toObjects(EventDto::class.java)
                ?.map { it.toDomain() }
                ?.filter { 
                    it.status == EventStatus.OPEN && it.endAt > now 
                }
                ?.sortedWith(
                    compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                        .thenByDescending { it.createdAt }
                ) ?: emptyList()

            trySend(events)
        }
        awaitClose { listener.remove() }
    }
}
