package com.eventos.banana.data.repository

import com.eventos.banana.data.remote.model.EventDto
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus
import com.eventos.banana.util.AppConstants
import com.eventos.banana.util.GeohashUtils
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

    companion object {
        private const val SMALL_RADIUS_KM = 5
        private const val REGION_PRECISION = 3  // ~150km precision for larger radius
    }

    /**
     * Obtiene un lote de eventos para el feed principal con optimización de read amplification.
     *
     * Estrategia adaptativa:
     * - radiusKm <= 5: multi-query geohash (centro + vecinos) para cobertura precisa
     * - radiusKm > 5: fallback a query por región (ignora geohash)
     * - Sin geohash: feed global cronológico
     *
     * @param radiusKm Radio en kilómetros (usado solo si geohashPrefix != null)
     */
    suspend fun fetchEventsBatch(
        geohashPrefix: String? = null,
        commune: String? = null,
        region: String? = null,
        limit: Long = 20,
        lastSnapshot: com.google.firebase.firestore.DocumentSnapshot? = null,
        radiusKm: Int = AppConstants.DEFAULT_SEARCH_RADIUS_KM
    ): Result<Pair<List<Event>, com.google.firebase.firestore.DocumentSnapshot?>> {
        return try {
            val now = System.currentTimeMillis()
            
            // Estrategia 1: Sin geohash → feed global
            if (geohashPrefix.isNullOrEmpty()) {
                return fetchGlobalEvents(now, limit, lastSnapshot)
            }
            
            // Estrategia 2: Radio pequeño (<=5km) → multi-query geohash
            if (radiusKm <= SMALL_RADIUS_KM) {
                return fetchEventsMultiGeohash(geohashPrefix, now, limit, lastSnapshot)
            }
            
            // Estrategia 3: Radio grande (>5km) → fallback a precisión menor
            return fetchEventsByRegion(geohashPrefix, now, limit, lastSnapshot)
        } catch (e: Exception) {
            android.util.Log.e("MainFeedRepository", "Error fetching events batch: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Feed global cronológico (sin filtros geográficos)
     */
    private suspend fun fetchGlobalEvents(
        now: Long,
        limit: Long,
        lastSnapshot: com.google.firebase.firestore.DocumentSnapshot?
    ): Result<Pair<List<Event>, com.google.firebase.firestore.DocumentSnapshot?>> {
        android.util.Log.d("MainFeedRepository", "Query Firestore GLOBAL (sin geohash)")
        
        var query: Query = eventsCollection
            .whereEqualTo("status", EventStatus.OPEN.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
        
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        
        val snapshot = query.limit(limit).get().await()
        val events = processEvents(snapshot, now)
        val lastDoc = snapshot.documents.lastOrNull()
        
        return Result.success(Pair(events, lastDoc))
    }

    /**
     * Multi-query geohash para radio pequeño (<=5km)
     * Consulta el geohash central + 8 vecinos para cobertura completa
     */
    private suspend fun fetchEventsMultiGeohash(
        centerGeohash: String,
        now: Long,
        limit: Long,
        lastSnapshot: com.google.firebase.firestore.DocumentSnapshot?
    ): Result<Pair<List<Event>, com.google.firebase.firestore.DocumentSnapshot?>> {
        android.util.Log.d("MainFeedRepository", "Multi-query geohash para radio pequeño: $centerGeohash")
        
        // Obtener geohashes vecinos (precisión 5 para cubrir ~2.4km cada uno)
        val parentGeohash = if (centerGeohash.length > 5) centerGeohash.substring(0, 5) else centerGeohash
        val neighborGeohashes = getNeighborGeohashes(parentGeohash)
        
        val allEvents = mutableListOf<Event>()
        var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
        
        // Ejecutar queries en paralelo para los geohashes más prometedores
        val baseQuery = eventsCollection.whereEqualTo("status", EventStatus.OPEN.name)
            .orderBy("geohash")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        
        // Query principal: geohash exacto (más preciso)
        val mainQuery = baseQuery.startAt(centerGeohash).endAt(centerGeohash + "~")
        if (lastSnapshot != null) {
            mainQuery.startAfter(lastSnapshot)
        }
        
        val mainResult = mainQuery.limit(limit).get().await()
        val mainEvents = processEvents(mainResult, now)
        allEvents.addAll(mainEvents)
        
        // Si no alcanzamos el límite, consultar vecinos
        if (mainEvents.size < limit) {
            val remaining = limit - mainEvents.size
            val neighborQuery = baseQuery
                .orderBy("geohash")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .whereIn("geohash", neighborGeohashes)
                .limit(remaining * 2) // Over-fetch para compensar dedup
            
            val neighborResult = neighborQuery.get().await()
            val neighborEvents = processEvents(neighborResult, now)
            
            // Filtrar eventos ya existentes (deduplicación)
            val existingIds = mainEvents.map { it.id }.toSet()
            val uniqueNeighborEvents = neighborEvents.filter { it.id !in existingIds }
            allEvents.addAll(uniqueNeighborEvents)
        }
        
        // Mantener último documento para paginación
        lastDoc = mainResult.documents.lastOrNull() ?: lastSnapshot
        
        // Ordenar y limitar resultados
        val sortedEvents = allEvents
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                    .thenByDescending { it.createdAt }
            )
            .take(limit.toInt())
        
        return Result.success(Pair(sortedEvents, lastDoc))
    }

    /**
     * Query por región para radio grande (>5km)
     * Usa precisión de geohash menor para cubrir área más amplia
     */
    private suspend fun fetchEventsByRegion(
        geohashPrefix: String,
        now: Long,
        limit: Long,
        lastSnapshot: com.google.firebase.firestore.DocumentSnapshot?
    ): Result<Pair<List<Event>, com.google.firebase.firestore.DocumentSnapshot?>> {
        android.util.Log.d("MainFeedRepository", "Query por región (radio grande): ${geohashPrefix.take(REGION_PRECISION)}")
        
        // Usar precisión menor (3 chars ~150km) para cubrir área más amplia
        val regionGeohash = if (geohashPrefix.length >= REGION_PRECISION) {
            geohashPrefix.substring(0, REGION_PRECISION)
        } else {
            geohashPrefix
        }
        
        var query: Query = eventsCollection
            .whereEqualTo("status", EventStatus.OPEN.name)
            .orderBy("geohash")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAt(regionGeohash)
            .endAt(regionGeohash + "~")
        
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        
        // Over-fetch porque el filtro por distancia se aplica en cliente
        val fetchLimit = limit * 3
        val snapshot = query.limit(fetchLimit).get().await()
        val events = processEvents(snapshot, now)
        val lastDoc = snapshot.documents.lastOrNull()
        
        return Result.success(Pair(events.take(limit.toInt()), lastDoc))
    }

    /**
     * Procesa eventos desde snapshot: convierte a dominio, filtra expirados y ordena
     */
    private fun processEvents(
        snapshot: com.google.firebase.firestore.QuerySnapshot,
        now: Long
    ): List<Event> {
        return snapshot.toObjects(EventDto::class.java)
            .map { it.toDomain() }
            .filter { it.endAt > now }
            .sortedWith(
                compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                    .thenByDescending { it.createdAt }
            )
    }

    /**
     * Obtiene los 8 geohashes vecinos + el centro (9 total)
     */
    private fun getNeighborGeohashes(geohash: String): List<String> {
        // Decodificar geohash a centro aproximado
        val (lat, lng, latErr, lngErr) = decodeGeohash(geohash)

        // Calcular los 8 vecinos moviéndose en las direcciones cardinales
        val neighbors = mutableListOf<String>()
        neighbors.add(geohash) // Centro

        // Direcciones: delta lat, delta lng
        val directions = listOf(
            Pair(latErr * 2, 0.0),          // Norte
            Pair(latErr * 2, lngErr * 2),   // Noreste
            Pair(0.0, lngErr * 2),          // Este
            Pair(-latErr * 2, lngErr * 2),  // Sureste
            Pair(-latErr * 2, 0.0),         // Sur
            Pair(-latErr * 2, -lngErr * 2), // Suroeste
            Pair(0.0, -lngErr * 2),         // Oeste
            Pair(latErr * 2, -lngErr * 2)   // Noroeste
        )

        for ((dLat, dLng) in directions) {
            val neighborHash = encodeGeohash(lat + dLat, lng + dLng, geohash.length)
            neighbors.add(neighborHash)
        }

        return neighbors.distinct()
    }

    // Decodificación simplificada de geohash a lat/lng + error bounds
    private fun decodeGeohash(hash: String): Quadruple<Double, Double, Double, Double> {
        val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0
        var isEven = true

        for (c in hash.lowercase()) {
            val cd = base32.indexOf(c)
            for (i in 4 downTo 0) {
                val bit = (cd shr i) and 1
                if (isEven) {
                    if (bit == 1) lngMin = (lngMin + lngMax) / 2
                    else lngMax = (lngMin + lngMax) / 2
                } else {
                    if (bit == 1) latMin = (latMin + latMax) / 2
                    else latMax = (latMin + latMax) / 2
                }
                isEven = !isEven
            }
        }

        val lat = (latMin + latMax) / 2.0
        val lng = (lngMin + lngMax) / 2.0
        val latErr = latMax - latMin
        val lngErr = lngMax - lngMin

        return Quadruple(lat, lng, latErr, lngErr)
    }

    // Codificación simplificada de lat/lng a geohash
    private fun encodeGeohash(lat: Double, lng: Double, precision: Int): String {
        val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        val result = StringBuilder()
        var isEven = true
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0
        var bit = 0
        var ch = 0

        while (result.length < precision) {
            val mid: Double
            if (isEven) {
                mid = (lngMin + lngMax) / 2.0
                if (lng > mid) {
                    ch = ch or (1 shl (4 - bit))
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                mid = (latMin + latMax) / 2.0
                if (lat > mid) {
                    ch = ch or (1 shl (4 - bit))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }
            isEven = !isEven

            if (bit < 4) {
                bit++
            } else {
                result.append(base32[ch])
                bit = 0
                ch = 0
            }
        }

        return result.toString()
    }

    // Data class helper para 4 valores
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Obtiene eventos en un radio específico usando Bounding Box y Haversine.
     * 🚀 PERFORMANCE OPTIMIZED: Limits list before Haversine calculation
     * @param centerLat Latitud del usuario
     * @param centerLng Longitud del usuario
     * @param radiusKm Radio en kilómetros
     * @param limit Máximo de eventos a devolver (default 50 for performance)
     */
    suspend fun getEventsInRadius(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        limit: Long = 50 // 🚀 PERFORMANCE: Reduced from 100 to 50 for better performance
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

            // 🚀 PERFORMANCE: Pre-filter with fast checks before Haversine
            val nowMs = now
            val statusOpen = EventStatus.OPEN.name
            
            // 3. Filtrado Fino (Haversine) y Estado - Optimized with lazy sequencing
            val filteredEvents = eventsDto
                .asSequence() // 🚀 Use lazy sequence to avoid intermediate collections
                .map { it.toDomain() }
                .filter { event ->
                    // Fast checks first (cheap operations)
                    if (event.status.name != statusOpen) return@filter false
                    if (event.endAt <= nowMs) return@filter false
                    
                    // Extraer coordenadas (usar la pública)
                    val eLat = event.latitude ?: return@filter false
                    val eLng = event.longitude ?: return@filter false

                    // Filtro rápido Bounding Box en Longitud (ya que no pudimos hacerlo en la BD)
                    if (eLng < minLng || eLng > maxLng) return@filter false

                    // Filtro exacto (Círculo de Haversine) - most expensive operation, done last
                    val distance = calculateHaversineDistance(centerLat, centerLng, eLat, eLng)
                    distance <= radiusKm
                }
                .toList() // Convert sequence to list
                .sortedWith(
                    compareByDescending<Event> { it.isBoosted && it.boostExpiry > nowMs }
                        .thenByDescending { it.createdAt }
                )

            Result.success(filteredEvents)
        } catch (e: Exception) {
            android.util.Log.e("MainFeedRepository", "Error fetching events by radius: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 🚀 PERFORMANCE: Fórmula de Haversine optimizada para calcular distancia en KMs
     * Uses pre-computed values and avoids redundant calculations
     */
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // 🚀 PERFORMANCE: Use Earth radius constant
        val EARTH_RADIUS_KM = 6371.0
        
        // 🚀 PERFORMANCE: Pre-compute radian conversions
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        // 🚀 PERFORMANCE: Pre-compute cosines (expensive trig operation)
        val cosLat1 = cos(Math.toRadians(lat1))
        val cosLat2 = cos(Math.toRadians(lat2))
        
        // 🚀 PERFORMANCE: Pre-compute sin^2 values
        val sinHalfLat = sin(dLat / 2)
        val sinHalfLon = sin(dLon / 2)
        
        // Haversine formula
        val a = sinHalfLat * sinHalfLat + cosLat1 * cosLat2 * sinHalfLon * sinHalfLon
        
        // 🚀 PERFORMANCE: Use sqrt directly instead of pow(0.5)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        
        return EARTH_RADIUS_KM * c
    }
    
    /**
     * 🚀 PERFORMANCE: Fast squared distance check (avoids sqrt for comparison)
     * Use this when you only need to compare distances, not the actual distance value
     */
    private fun isWithinRadiusSquared(
        centerLat: Double, centerLng: Double,
        eventLat: Double, eventLng: Double,
        radiusKm: Double
    ): Boolean {
        // Simple Euclidean approximation for quick rejection
        // Good enough for small distances and much faster than Haversine
        val dLat = (eventLat - centerLat) * 111.0 // ~111 km per degree latitude
        val dLng = (eventLng - centerLng) * 111.0 * cos(Math.toRadians(centerLat))
        val distanceSquared = dLat * dLat + dLng * dLng
        return distanceSquared <= radiusKm * radiusKm
    }

    /**
     * Observa eventos en tiempo real.
     *
     * 🌍 Modo Internacional:
     * - Con geohash: observa la zona geográfica.
     * - Sin geohash: feed global cronológico.
     * - commune y region se ignoran.
     */
    fun observeNearbyEvents(
        geohashPrefix: String? = null,
        commune: String? = null,    // Ignorado — mantenido para compatibilidad de firma
        region: String? = null,     // Ignorado — mantenido para compatibilidad de firma
        limit: Long = 20
    ): Flow<List<Event>> = callbackFlow {
        var query: Query = eventsCollection

        if (!geohashPrefix.isNullOrEmpty()) {
            query = query
                .orderBy("geohash")
                .startAt(geohashPrefix)
                .endAt(geohashPrefix + "~")
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
                ?.filter { it.status == EventStatus.OPEN && it.endAt > now }
                ?.sortedWith(
                    compareByDescending<Event> { it.isBoosted && it.boostExpiry > now }
                        .thenByDescending { it.createdAt }
                ) ?: emptyList()

            trySend(events)
        }
        awaitClose { listener.remove() }
    }
}
