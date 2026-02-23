package com.eventos.banana.core.utils

sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Error(val error: UiError) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
}

inline fun <T> Result<T>.toAppResult(): AppResult<T> {
    return fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { 
            val uiError = when (it) {
                // Aquí se puede agregar mapeo custom de excepciones comunes
                is java.net.UnknownHostException, is java.net.ConnectException -> UiError.NetworkError()
                else -> UiError.UnknownError(exception = it)
            }
            AppResult.Error(uiError)
        }
    )
}
