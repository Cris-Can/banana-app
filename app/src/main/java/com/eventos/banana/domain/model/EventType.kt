package com.eventos.banana.domain.model

enum class EventType(val displayName: String, val emoji: String) {
    DEPORTES("Deportes", "⚽"),
    SOCIAL("Social", "🎉"),
    CULTURAL("Cultural", "🎭"),
    EDUCATIVO("Educativo", "📚"),
    JUEGOS("Juegos", "🎮"),
    GASTRONOMIA("Gastronomía", "🍽️"),
    AIRE_LIBRE("Aire Libre", "🏕️"),
    OTRO("Otro", "🔹");

    companion object {
        fun fromString(value: String): EventType {
            return values().find { it.name == value } ?: OTRO
        }
    }
}
