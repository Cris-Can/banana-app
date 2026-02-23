package com.eventos.banana.core.data

/**
 * Wrapper estandarizado para resultados de operaciones de datos.
 * Proporciona una forma consistente de manejar éxitos y errores.
 */
sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Error(val throwable: Throwable, val message: String? = null) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.throwable
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Extensión para convertir un Result de Kotlin a AppResult
 */
fun <T> Result<T>.toAppResult(): AppResult<T> {
    return if (this.isSuccess) {
        AppResult.Success(this.getOrThrow())
    } else {
        AppResult.Error(this.exceptionOrNull() ?: Exception("Error desconocido"))
    }
}
