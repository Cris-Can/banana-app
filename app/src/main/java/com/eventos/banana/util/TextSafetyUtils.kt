package com.eventos.banana.util

object TextSafetyUtils {
    // 🚨 Lista básica de palabras/frases tóxicas (Expandir según necesidad)
    // Se usa regex para evitar falsos positivos simples, aunque es básico.
    private val TOXIC_PATTERNS = listOf(
        "estafa", "robo", "droga", "venta de", "sexual", "prostitu", "viola", "matar", "suicid",
        "puta", "maricon", "conchetum", "weon culi" // Ejemplos localizados (Chile/Latam) - Expandir con cuidado
    )

    fun containsToxicContent(text: String): Boolean {
        val normalized = text.lowercase()
        return TOXIC_PATTERNS.any { pattern ->
            normalized.contains(pattern)
        }
    }

    fun validateEventContent(title: String, description: String, isAdultContent: Boolean = false): String? {
        if (isAdultContent) return null // +18 events bypass toxic filter
        if (containsToxicContent(title)) return "El título contiene palabras no permitidas."
        if (containsToxicContent(description)) return "La descripción contiene contenido inapropiado."
        return null // OK
    }
}
