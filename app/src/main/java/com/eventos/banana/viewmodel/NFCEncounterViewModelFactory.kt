package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.eventos.banana.data.repository.EncounterRepository
import com.eventos.banana.data.repository.UserRepository

class NFCEncounterViewModelFactory(
    private val eventId: String,
    private val currentUserId: String,
    private val participantIds: List<String>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NFCEncounterViewModel::class.java)) {
            return NFCEncounterViewModel(
                eventId = eventId,
                currentUserId = currentUserId,
                participantIds = participantIds,
                encounterRepository = EncounterRepository(),
                userRepository = UserRepository()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
