package com.eventos.banana.domain.model

/**
 * Representa un "encuentro" confirmado entre dos usuarios en un evento.
 * Se registra cuando:
 * - Hacen NFC tap (prioridad)
 * - Ambos hacen check-in GPS en el evento (fallback)
 * - El creador confirma manualmente (override)
 */
data class Encounter(
    val encounterId: String = "",
    val eventId: String = "",
    val userId1: String = "", // Menor UID alfabéticamente (para evitar duplicados)
    val userId2: String = "", // Mayor UID alfabéticamente
    val method: EncounterMethod = EncounterMethod.NFC_TAP,
    val timestamp: Long = System.currentTimeMillis(),
    val location: EncounterLocation? = null,
    val confirmedByCreator: Boolean = false
) {
    companion object {
        fun fromMap(map: Map<String, Any>): Encounter {
            return Encounter(
                encounterId = map["encounterId"] as? String ?: "",
                eventId = map["eventId"] as? String ?: "",
                userId1 = map["userId1"] as? String ?: "",
                userId2 = map["userId2"] as? String ?: "",
                method = try {
                    EncounterMethod.valueOf(map["method"] as? String ?: "NFC_TAP")
                } catch (e: Exception) {
                    EncounterMethod.NFC_TAP
                },
                timestamp = map["timestamp"] as? Long ?: 0L,
                location = (map["location"] as? Map<String, Any>)?.let {
                    EncounterLocation.fromMap(it)
                },
                confirmedByCreator = map["confirmedByCreator"] as? Boolean ?: false
            )
        }

        /**
         * Helper: Crear ID único para el encuentro (evita duplicados)
         */
        fun createId(eventId: String, uid1: String, uid2: String): String {
            val sorted = listOf(uid1, uid2).sorted()
            return "${eventId}_${sorted[0]}_${sorted[1]}"
        }
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "encounterId" to encounterId,
            "eventId" to eventId,
            "userId1" to userId1,
            "userId2" to userId2,
            "method" to method.name,
            "timestamp" to timestamp,
            "confirmedByCreator" to confirmedByCreator
        )
        location?.let {
            map["location"] = it.toMap()
        }
        return map
    }
}

enum class EncounterMethod {
    NFC_TAP,           // Usuario tapeó su teléfono con otro
    GPS_CHECK_IN,      // Ambos hicieron check-in GPS al evento
    MANUAL_OVERRIDE    // Creador confirmó manualmente el encuentro
}

data class EncounterLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f // Precisión en metros
) {
    companion object {
        fun fromMap(map: Map<String, Any>): EncounterLocation {
            return EncounterLocation(
                latitude = map["latitude"] as? Double ?: 0.0,
                longitude = map["longitude"] as? Double ?: 0.0,
                accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy
        )
    }
}
