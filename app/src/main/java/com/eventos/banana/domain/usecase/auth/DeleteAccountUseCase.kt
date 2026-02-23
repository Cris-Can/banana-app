package com.eventos.banana.domain.usecase.auth

import com.eventos.banana.data.repository.AuthRepository

class DeleteAccountUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = authRepository.deleteAccount()
        return if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Error desconocido"
            if (error.contains("recent-login", ignoreCase = true) || error.contains("requires-recent-login", ignoreCase = true)) {
                Result.failure(Exception("⚠️ Por seguridad, debes cerrar sesión y volver a entrar para eliminar tu cuenta."))
            } else {
                Result.failure(Exception("❌ Error: $error"))
            }
        }
    }
}
