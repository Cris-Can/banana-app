package com.eventos.banana.domain.usecase.auth

import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.UserRepository

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<Boolean> {
        val result = authRepository.login(email, password)
        
        return if (result.isSuccess) {
            try {
                authRepository.reloadUser()
                val isEmailVerified = authRepository.isEmailVerified()
                
                if (isEmailVerified) {
                    val uid = authRepository.currentUid()
                    if (uid != null) {
                        userRepository.updateVerificationStatus(uid, true)
                    }
                }
                Result.success(isEmailVerified)
            } catch (e: Exception) {
                // If sync fails, just return current verification status
                val isEmailVerified = authRepository.isEmailVerified()
                Result.success(isEmailVerified)
            }
        } else {
            Result.failure(Exception("Email o contraseña incorrectos"))
        }
    }
}
