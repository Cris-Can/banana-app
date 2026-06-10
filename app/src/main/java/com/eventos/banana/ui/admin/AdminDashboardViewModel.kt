package com.eventos.banana.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import timber.log.Timber

data class PendingVerification(
    val user: UserProfile,
    val photoUrl: String? = null
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isLoadingVerifications: Boolean = true,
        val reports: List<Map<String, Any>> = emptyList(),
        val pendingVerifications: List<PendingVerification> = emptyList(),
        val showPhotoDialogUid: String? = null,
        val photoUrl: String? = null,
        val snackbarMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isLoadingVerifications = true) }

            // Load Reports
            val reportsList = userRepository.getPendingReports()

            // Load Pending Identity Verifications
            val pendingUsers = getPendingIdentityVerifications()

            // Create wrappers
            val pendingVerificationsList = pendingUsers.map { user ->
                PendingVerification(user = user, photoUrl = null) // We'll load photos individually or lazily
            }

            _uiState.update {
                it.copy(
                    reports = reportsList as List<Map<String, Any>>,
                    isLoading = false,
                    pendingVerifications = pendingVerificationsList,
                    isLoadingVerifications = false
                )
            }

            // After loading the list, pre-fetch the photos
            pendingVerificationsList.forEach { pending ->
                loadPhotoUrlForVerification(pending.user.uid)
            }
        }
    }

    private suspend fun getPendingIdentityVerifications(): List<UserProfile> {
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("identityVerificationStatus", "pending")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(com.eventos.banana.data.remote.model.UserProfileDto::class.java)?.toDomain()?.copy(uid = doc.id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Verification error")
            emptyList()
        }
    }

    private fun loadPhotoUrlForVerification(uid: String) {
        viewModelScope.launch {
            try {
                val url = storage.reference
                    .child("identityDocs/${uid}/front.jpg")
                    .downloadUrl
                    .await()
                    .toString()

                _uiState.update { state ->
                    val updatedList = state.pendingVerifications.map {
                        if (it.user.uid == uid) it.copy(photoUrl = url) else it
                    }
                    state.copy(pendingVerifications = updatedList)
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminDashboardVM", "Failed to load photo for $uid", e)
            }
        }
    }

    fun approveVerification(uid: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "identityVerified" to true,
                        "identityVerificationStatus" to "approved",
                        "identityVerifiedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val adminUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val notificationData = mapOf(
                    "userId" to uid,
                    "fromUserId" to adminUid,
                    "title" to "Identidad Aprobada",
                    "message" to "Tu verificación de identidad ha sido aprobada. Ya puedes crear y ver eventos +18.",
                    "type" to com.eventos.banana.domain.model.NotificationType.GENERIC.name,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("notifications").add(notificationData).await()

                _uiState.update { state ->
                    state.copy(
                        pendingVerifications = state.pendingVerifications.filter { it.user.uid != uid },
                        snackbarMessage = "Verificación aprobada"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Error al aprobar: ${e.message}") }
            }
        }
    }

    fun rejectVerification(uid: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update(
                    mapOf("identityVerificationStatus" to "rejected")
                ).await()

                val adminUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val notificationData = mapOf(
                    "userId" to uid,
                    "fromUserId" to adminUid,
                    "title" to "Identidad Rechazada",
                    "message" to "Tu verificación de identidad ha sido rechazada. Si crees que es un error, contacta al soporte.",
                    "type" to com.eventos.banana.domain.model.NotificationType.GENERIC.name,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("notifications").add(notificationData).await()

                _uiState.update { state ->
                    state.copy(
                        pendingVerifications = state.pendingVerifications.filter { it.user.uid != uid },
                        snackbarMessage = "Verificación rechazada"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Error al rechazar: ${e.message}") }
            }
        }
    }

    fun resolveReport(reportId: String, action: String) {
        viewModelScope.launch {
            userRepository.resolveReport(reportId, action)
            _uiState.update { state ->
                state.copy(reports = state.reports.filter { it["id"] != reportId })
            }
        }
    }

    fun banUser(uid: String, reportId: String) {
        viewModelScope.launch {
            userRepository.banUser(uid)
            userRepository.resolveReport(reportId, "BANNED")
            _uiState.update { state ->
                state.copy(reports = state.reports.filter { it["id"] != reportId })
            }
        }
    }

    fun showPhotoDialog(uid: String) {
        val url = _uiState.value.pendingVerifications.find { it.user.uid == uid }?.photoUrl
        _uiState.update { it.copy(showPhotoDialogUid = uid, photoUrl = url) }
    }

    fun dismissPhotoDialog() {
        _uiState.update { it.copy(showPhotoDialogUid = null, photoUrl = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
