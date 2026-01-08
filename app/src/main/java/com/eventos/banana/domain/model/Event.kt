package com.eventos.banana.domain.model

data class Event(

    // ---------------- IDENTIDAD ----------------
    val id: String = "",
    val creatorId: String = "",

    // ---------------- INFO BÁSICA ----------------
    val title: String = "",
    val description: String = "",
    val category: String = "",

    // ---------------- UBICACIÓN ----------------
    val country: String = "Chile",
    val region: String = "",
    val commune: String = "",

    // ---------------- TIEMPO ----------------
    val eventTimestamp: Long = 0L,
    val createdAt: Long = 0L,

    // ---------------- CAPACIDAD ----------------
    val maxParticipants: Int = 0,

    // ---------------- REGLAS ----------------
    val minScoreRequired: Int = 0,
    val approvalRequired: Boolean = true,

    // ---------------- ESTADO GENERAL ----------------
    val status: EventStatus = EventStatus.OPEN,

    // ---------------- PARTICIPACIÓN (ÚNICA FUENTE DE VERDAD) ----------------
    val approvedParticipants: List<String> = emptyList(),
    val pendingRequests: List<String> = emptyList(),
    val rejectedParticipants: List<String> = emptyList()
)
