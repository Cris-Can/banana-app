package com.eventos.banana.domain.model

enum class NotificationType {

    GENERIC,

    // Solicitudes
    JOIN_REQUEST_SENT,
    JOIN_APPROVED,
    JOIN_REJECTED,

    // Eventos
    EVENT_CREATED,
    EVENT_CANCELLED,     // 👈 A15.1
    EVENT_CLOSED,        // 👈 A15.1

    // Moderación
    REMOVED_FROM_EVENT   // 👈 A15.1
}
