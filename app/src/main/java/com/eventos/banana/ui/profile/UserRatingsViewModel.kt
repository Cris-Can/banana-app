package com.eventos.banana.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.RatingRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserRating
import com.eventos.banana.domain.model.UserProfile
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.DocumentSnapshot
import timber.log.Timber

data class UserRatingsUiState(
    val isLoading: Boolean = false,
    val isPaginatedLoading: Boolean = false,
    val ratings: List<RatingWithUser> = emptyList(),
    val targetProfile: UserProfile? = null,
    val errorMessage: String? = null,
    val hasMore: Boolean = true,
    val credits: Int = 0,
    val creditsExpiry: Long = 0L,
    val revealedRatingIds: List<String> = emptyList(),
    val viewerProfile: UserProfile? = null
)

data class RatingWithUser(
    val rating: UserRating,
    val fromUserNickname: String? = null
)

@HiltViewModel(assistedFactory = UserRatingsViewModel.Factory::class)
class UserRatingsViewModel @AssistedInject constructor(
    @Assisted("targetUserId") private val targetUserId: String,
    @Assisted("isViewerPremium") private val isViewerPremium: Boolean,
    private val ratingRepository: RatingRepository,
    private val userRepository: UserRepository,
    private val authRepository: com.eventos.banana.data.repository.AuthRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("targetUserId") targetUserId: String,
            @Assisted("isViewerPremium") isViewerPremium: Boolean
        ): UserRatingsViewModel
    }

    private val _uiState = MutableStateFlow(UserRatingsUiState(isLoading = true))
    val uiState: StateFlow<UserRatingsUiState> = _uiState

    private var lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null

    init {
        loadRatings(isInitial = true)
    }

    fun loadRatings(isInitial: Boolean = false) {
        if (!isInitial && (!_uiState.value.hasMore || _uiState.value.isPaginatedLoading)) return

        viewModelScope.launch {
            if (isInitial) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                lastDocument = null
            } else {
                _uiState.value = _uiState.value.copy(isPaginatedLoading = true)
            }
            
            try {
                // 1. Get target profile only on initial load
                val profile = if (isInitial) {
                    userRepository.getUserProfile(targetUserId)
                } else {
                    _uiState.value.targetProfile
                }

                val currentUserId = authRepository.currentUid()
                val viewerProfile = if (isInitial && currentUserId != null) {
                    userRepository.getUserProfile(currentUserId)
                } else {
                    _uiState.value.viewerProfile
                }
                
                // 2. Get ratings using privacy logic and pagination
                val result = ratingRepository.getUserRatings(
                    userId = targetUserId,
                    isPremium = isViewerPremium,
                    revealedIds = viewerProfile?.revealedRatingIds ?: emptyList(),
                    limit = 20,
                    startAfter = lastDocument
                )
                
                if (result.isSuccess) {
                    val (rawRatings, newLastDoc) = result.getOrNull().also {
                        if (it == null) {
                            Timber.w("getUserRatings returned success with null value")
                        }
                    } ?: Pair(emptyList(), null)
                    lastDocument = newLastDoc
                    
                    // 3. Enrich with nicknames if Premium
                    val enrichedRatings = if (isViewerPremium) {
                        rawRatings.map { rating ->
                            val nickname = if (rating.fromUserId != "anonymous") {
                                userRepository.getUserProfile(rating.fromUserId)?.nickname
                            } else null
                            RatingWithUser(rating, nickname)
                        }
                    } else {
                        rawRatings.map { RatingWithUser(it) }
                    }

                    val currentRatings = if (isInitial) emptyList() else _uiState.value.ratings
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPaginatedLoading = false,
                        ratings = currentRatings + enrichedRatings,
                        targetProfile = profile,
                        viewerProfile = viewerProfile,
                        credits = viewerProfile?.ratingCredits ?: 0,
                        creditsExpiry = viewerProfile?.ratingCreditsExpiry ?: 0L,
                        revealedRatingIds = viewerProfile?.revealedRatingIds ?: emptyList(),
                        hasMore = rawRatings.size >= 20
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPaginatedLoading = false,
                        errorMessage = "Error al cargar calificaciones: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPaginatedLoading = false,
                    errorMessage = "Error inesperado: ${e.message}"
                )
            }
        }
    }

    fun revealRater(ratingId: String) {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUid() ?: return@launch
            val result = ratingRepository.revealRating(ratingId, currentUserId)
            if (result.isSuccess) {
                // Recargar ratings para reflejar el nombre revelado y actualizar créditos
                loadRatings(isInitial = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al revelar: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun anonymizeMyRating(ratingId: String) {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUid() ?: return@launch
            val result = ratingRepository.anonymizeRating(ratingId, currentUserId)
            if (result.isSuccess) {
                loadRatings(isInitial = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al ocultar: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
