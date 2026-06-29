package com.eventos.banana.domain.usecase.auth

import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.core.error.ErrorMapper
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = authRepository.deleteAccount()
        return if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Error desconocido al eliminar cuenta")
            val error = ErrorMapper.mapToRepositoryError(exception, "deleteAccount")
            Result.failure(Exception(error.message))
        }
    }
}
