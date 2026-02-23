package com.eventos.banana.domain.usecase.profile

import com.eventos.banana.data.repository.UserRepository

class ManageFriendsUseCase(
    private val userRepository: UserRepository
) {
    suspend fun sendFriendRequest(currentUid: String, targetUid: String): Result<Unit> {
        return try {
            userRepository.sendFriendRequest(currentUid, targetUid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(currentUid: String, requesterUid: String): Result<Unit> {
        return try {
            userRepository.acceptFriendRequest(currentUid, requesterUid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
