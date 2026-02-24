package com.eventos.banana.util

import java.util.Calendar

object AgeCalculator {
    
    /**
     * Calcula la edad a partir de una fecha de nacimiento (timestamp)
     * @param birthDateMillis Timestamp en milisegundos de la fecha de nacimiento
     * @return Edad en años completos
     */
    fun calculateAge(birthDateMillis: Long): Int {
        val birthDate = java.time.Instant.ofEpochMilli(birthDateMillis)
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
        val today = java.time.LocalDate.now()
        return java.time.Period.between(birthDate, today).years
    }
    
    /**
     * Valida si una persona es mayor de edad (18+)
     * @param birthDateMillis Timestamp de fecha de nacimiento
     * @return true si tiene 18 años o más
     */
    fun isAdult(birthDateMillis: Long): Boolean {
        return calculateAge(birthDateMillis) >= 18
    }
    
    /**
     * Obtiene la edad mínima permitida en timestamp
     * @return Timestamp máximo para tener exactamente 18 años hoy
     */
    fun getMinimumBirthDate(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -18)
        return calendar.timeInMillis
    }
    
    /**
     * Obtiene timestamp para una edad específica (usado para limitar date picker)
     * @param maxAge Edad máxima (ej: 100 años)
     * @return Timestamp
     */
    fun getMaxBirthDate(maxAge: Int = 100): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -maxAge)
        return calendar.timeInMillis
    }
}
