package com.eventos.banana.domain.model

enum class DateFilter(val displayName: String) {
    ALL("Cualquier fecha"),
    TODAY("Hoy"),
    TOMORROW("Mañana"),
    WEEKEND("Este Finde")
}
