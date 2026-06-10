package com.eventos.banana.domain.usecase.profile

import com.eventos.banana.data.repository.UserRepository
import javax.inject.Inject

class UploadProfilePhotoUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        uid: String, 
        imageBytes: ByteArray?, 
        isProfilePicture: Boolean = false, 
        isCoverPhoto: Boolean = false
    ): Result<Unit> {
        if (imageBytes == null) return Result.failure(Exception("Bytes de imagen nulos"))
        
        try {
            // Validar límite de fotos
            if (!isProfilePicture && !isCoverPhoto) {
                val profile = userRepository.getUserProfile(uid)
                if (profile != null && profile.photos.size >= 6) {
                    return Result.failure(Exception("Límite de 6 fotos alcanzado (máx. 6)"))
                }
            }
            
            val result = userRepository.uploadProfilePhoto(uid, imageBytes, isProfilePicture, isCoverPhoto)
            
            return if (result.isSuccess) {
                Result.success(Unit)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error desconocido"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
