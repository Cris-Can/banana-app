package com.eventos.banana.domain.usecase.auth

import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.UserRepository

import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<Boolean> {
        val result = authRepository.login(email, password)

        return if (result.isSuccess) {
            val isEmailVerified = authRepository.isEmailVerified()

            if (isEmailVerified) {
                val uid = authRepository.currentUid()
                if (uid != null) {
                    try {
                        userRepository.updateVerificationStatus(uid, true)
                    } catch (e: Exception) {
                        android.util.Log.w("LoginUseCase", "Failed to sync verification status", e)
                    }
                }
            }
            Result.success(isEmailVerified)
        } else {
            Result.failure(Exception("Email o contraseña incorrectos"))
        }
    }
}
