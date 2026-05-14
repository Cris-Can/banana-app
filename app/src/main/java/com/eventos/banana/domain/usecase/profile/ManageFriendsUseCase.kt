package com.eventos.banana.domain.usecase.profile

import com.eventos.banana.data.repository.UserRepository

class ManageFriendsUseCase(
    private val userRepository: UserRepository
) {
    suspend fun sendFriendRequest(targetUid: String): Result<Unit> {
        return userRepository.sendFriendRequest(targetUid)
    }

    suspend fun acceptFriendRequest(requesterUid: String): Result<Unit> {
        return userRepository.acceptFriendRequest(requesterUid)
    }
}
