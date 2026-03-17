package com.eventos.banana.ui.util

/**
 * Representa un estado genérico de una operación de UI (UI State pattern)
 */
sealed class ResultState<out T> {
    object Idle : ResultState<Nothing>()
    object Loading : ResultState<Nothing>()
    data class Success<out T>(val data: T) : ResultState<T>()
    data class Error(val message: String) : ResultState<Nothing>()
}
