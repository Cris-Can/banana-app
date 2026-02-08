package com.eventos.banana.domain.model

enum class EventType(val displayName: String, val emoji: String, val subcategories: List<String>) {
    DEPORTES("Deportes", "⚽", listOf("Fútbol", "Calistenia", "Gym", "Tenis", "Pádel", "Running", "Ciclismo", "Trekking", "Basket", "Voleibol", "Yoga", "Crossfit")),
    SOCIAL("Social", "🎉", listOf("Fiesta", "Previa", "Bar", "Boliche", "Cita", "Reunión", "Cumpleaños", "Networking")),
    CULTURAL("Cultural", "🎭", listOf("Cine", "Teatro", "Museo", "Concierto", "Arte", "Stand-up", "Lectura", "Música en vivo")),
    EDUCATIVO("Educativo", "📚", listOf("Taller", "Curso", "Idiomas", "Estudio", "Hackathon", "Charla", "Tutoría")),
    JUEGOS("Juegos", "🎮", listOf("Videojuegos", "Juegos de Mesa", "Rol", "Cartas", "Escape Room", "Trivia", "Casino")),
    GASTRONOMIA("Gastronomía", "🍽️", listOf("Cena", "Almuerzo", "Café", "Asado", "Foodtrucks", "Cata", "Cocina")),
    AIRE_LIBRE("Aire Libre", "🏕️", listOf("Parque", "Playa", "Camping", "Pesca", "Picnic", "Caminata", "Escalada")),
    OTRO("Otro", "🔹", emptyList());

    companion object {
        fun fromString(value: String): EventType {
            return values().find { it.name == value } ?: OTRO
        }
    }
}
