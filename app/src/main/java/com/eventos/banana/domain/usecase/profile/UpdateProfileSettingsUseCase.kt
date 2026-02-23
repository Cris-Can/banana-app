package com.eventos.banana.domain.usecase.profile

import com.eventos.banana.data.repository.UserRepository

class UpdateProfileSettingsUseCase(
    private val userRepository: UserRepository
) {
    suspend fun updateAppTheme(uid: String, theme: String): Result<Unit> {
        return try {
            userRepository.updateAppTheme(uid, theme)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLocation(uid: String, region: String, commune: String): Result<Unit> {
        return try {
            userRepository.updateLocation(uid, region, commune)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLocationFromDevice(uid: String, region: String, commune: String, lat: Double, lng: Double): Result<Unit> {
        return try {
            val geohash = com.eventos.banana.util.GeohashUtils.encode(lat, lng, 9)
            userRepository.updateLocation(uid, region, commune, lat, lng, geohash)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNotifyEventsByCommune(uid: String, enabled: Boolean, region: String?, commune: String?): Result<Unit> {
        return try {
            userRepository.updateNotifyEventsByCommune(uid, enabled, region, commune)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleCategorySubscription(uid: String, categoryTopic: String, isEnabled: Boolean): Result<Unit> {
        val currentProfile = userRepository.getUserProfile(uid) ?: return Result.failure(Exception("Perfil no encontrado"))
        val currentSubscriptions = currentProfile.subscribedCategories.toMutableList()
        
        if (isEnabled) {
            if (!currentSubscriptions.contains(categoryTopic)) {
                currentSubscriptions.add(categoryTopic)
            }
        } else {
            currentSubscriptions.remove(categoryTopic)
        }
        
        return try {
            userRepository.updateSubscribedCategories(uid, currentSubscriptions)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
