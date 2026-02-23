package com.eventos.banana.core.utils

sealed class UiError {
    data class NetworkError(val message: String = "Sin conexión a internet") : UiError()
    data class AuthError(val message: String = "Error de autenticación") : UiError()
    data class ServerError(val message: String = "Error interno del servidor") : UiError()
    data class UnknownError(val message: String = "Ocurrió un error inesperado", val exception: Throwable? = null) : UiError()
    data class GenericError(val message: String) : UiError()

    fun getErrorMessage(): String = when (this) {
        is NetworkError -> message
        is AuthError -> message
        is ServerError -> message
        is UnknownError -> exception?.localizedMessage ?: message
        is GenericError -> message
    }
}
