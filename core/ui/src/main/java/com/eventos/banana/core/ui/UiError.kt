package com.eventos.banana.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Clase estandarizada para representar errores en la UI.
 * Facilita la presentación de mensajes amigables y acciones de recuperación.
 */
data class UiError(
    val message: String,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null
) {
    companion object {
        fun fromThrowable(t: Throwable, onRetry: (() -> Unit)? = null): UiError {
            // Aquí se pueden mapear excepciones específicas a mensajes amigables
            return UiError(
                message = t.message ?: "Ocurrió un error inesperado",
                actionText = if (onRetry != null) "Reintentar" else null,
                onAction = onRetry
            )
        }
    }
}
