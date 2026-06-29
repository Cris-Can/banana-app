package com.eventos.banana.domain.usecase.auth

import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.util.AgeCalculator
import com.eventos.banana.util.AppConstants
import com.eventos.banana.util.GeohashUtils
import com.eventos.banana.core.error.ErrorMapper

import com.eventos.banana.domain.usecase.profile.CreateUserProfileUseCase
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val createUserProfileUseCase: CreateUserProfileUseCase
) {
    suspend operator fun invoke(
        email: String, 
        password: String, 
        nickname: String,
        birthDate: Long,
        commune: String,
        region: String? = null,
        country: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        invitationCode: String? = null
    ): Result<Unit> {
        val age = AgeCalculator.calculateAge(birthDate)
        if (age < 18) {
            return Result.failure(Exception("❌ Debes ser mayor de 18 años para usar esta app"))
        }

        val result = authRepository.register(email, password)

        return if (result.isSuccess) {
            val uid = authRepository.currentUid() ?: return Result.failure(Exception("Usuario no autenticado"))

            val finalRegion = region ?: ""
            val geohash = if (latitude != null && longitude != null) {
                GeohashUtils.encode(latitude, longitude, GeohashUtils.getPrecisionForRadius(AppConstants.DEFAULT_SEARCH_RADIUS_KM))
            } else null

            val profile = UserProfile(
                uid = uid,
                email = email,
                nickname = nickname,
                birthDate = birthDate,
                age = age,
                commune = commune,
                region = finalRegion,
                country = country,
                latitude = latitude,
                longitude = longitude,
                geohash = geohash,
                invitationCode = invitationCode
            )

            try {
                createUserProfileUseCase(profile)
                val verificationResult = authRepository.sendEmailVerification()
                if (verificationResult.isFailure) {
                    android.util.Log.w("RegisterUseCase", "Account created but verification email failed: ${verificationResult.exceptionOrNull()?.message}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("RegisterUseCase", "CRITICAL: Failed to create profile for $uid: ${e.message}", e)
                Result.failure(Exception("Cuenta creada pero error al guardar perfil: ${e.message}"))
            }

        } else {
            val exception = result.exceptionOrNull() ?: Exception("Unknown error during registration")
            val error = ErrorMapper.mapToRepositoryError(exception, "register")
            Result.failure(Exception(error.message))
        }
    }
}
