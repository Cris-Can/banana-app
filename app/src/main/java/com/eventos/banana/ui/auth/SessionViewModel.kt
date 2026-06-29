package com.eventos.banana.ui.auth

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.BiometricRepository
import com.eventos.banana.util.AppConstants
import com.eventos.banana.util.BiometricHelper
import com.eventos.banana.util.GeohashUtils
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.LoginUiState
import com.eventos.banana.domain.model.RegisterUiState
import com.eventos.banana.domain.model.ProfileUiState
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.eventos.banana.core.error.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: com.eventos.banana.data.repository.NotificationRepository,
    private val loginUseCase: com.eventos.banana.domain.usecase.auth.LoginUseCase,
    private val registerUseCase: com.eventos.banana.domain.usecase.auth.RegisterUseCase,
    private val createUserProfileUseCase: com.eventos.banana.domain.usecase.profile.CreateUserProfileUseCase,
    private val deleteAccountUseCase: com.eventos.banana.domain.usecase.auth.DeleteAccountUseCase,
    private val sharedPreferences: android.content.SharedPreferences,
    private val biometricRepository: BiometricRepository
) : androidx.lifecycle.AndroidViewModel(application) {

    private var profileJob: kotlinx.coroutines.Job? = null
    private var notificationsJob: kotlinx.coroutines.Job? = null

    // ---------- UI STATE (LOGIN / REGISTER) ----------
    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState

    private val _registerUiState = MutableStateFlow(RegisterUiState())
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState

    // ---------- UI STATE (PROFILE) ----------
    private val _profileUiState = MutableStateFlow(ProfileUiState())
    val profileUiState: StateFlow<ProfileUiState> = _profileUiState

    // ---------- SESSION STATE ----------
    private val _sessionState = MutableStateFlow(SessionState.LOADING)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _unreadNotificationsCount = MutableStateFlow(0)
    val unreadNotificationsCount: StateFlow<Int> = _unreadNotificationsCount

    private val _hasBiometricCredentials = MutableStateFlow(false)
    val hasBiometricCredentials: StateFlow<Boolean> = _hasBiometricCredentials.asStateFlow()

    // 📍 Location Update Feedback
    private val _locationUpdateMessage = MutableStateFlow<String?>(null)
    val locationUpdateMessage: StateFlow<String?> = _locationUpdateMessage.asStateFlow()
    
    fun clearLocationMessage() {
        _locationUpdateMessage.value = null
    }

    // Email verification result feedback
    private val _sendVerificationMessage = MutableStateFlow<String?>(null)
    val sendVerificationMessage: StateFlow<String?> = _sendVerificationMessage.asStateFlow()

    fun clearSendVerificationMessage() {
        _sendVerificationMessage.value = null
    }



    // =====================================================
    // SESSION CHECK (APP START)
    // =====================================================
    // 🔒 Initialize from cache to avoid verification screen flash
    var isEmailVerified by androidx.compose.runtime.mutableStateOf(
        sharedPreferences?.getBoolean("email_verified_cache", false) ?: false
    )
        private set
    
    // 🔒 Track if verification check is complete to prevent premature navigation
    var isVerificationChecked by androidx.compose.runtime.mutableStateOf(false)
        private set

    private fun checkSession() {
        if (authRepository.isUserLoggedIn()) {
            _sessionState.value = SessionState.AUTHENTICATED
            viewModelScope.launch {
                try {
                    // 🆕 Force reload to pick up fresh email_verified status
                    authRepository.reloadUser()
                    isEmailVerified = authRepository.isEmailVerified()
                    // 🔒 Cache verification state
                    sharedPreferences.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
                    android.util.Log.d("SessionViewModel", "Session check: reload success. Verified: $isEmailVerified")
                    if (isEmailVerified) {
                        val uid = authRepository.currentUid() ?: return@launch
                        userRepository.updateVerificationStatus(uid, true)
                        android.util.Log.d("SessionViewModel", "Session check: isVerified sync success")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.e("SessionViewModel", "Session check: reload/sync FAILED: ${e.message}")
                    isEmailVerified = authRepository.isEmailVerified()
                } finally {
                    // 🔒 Mark verification check as complete
                    isVerificationChecked = true
                }
                loadUserProfile()
                registerFcmToken()
                observeNotifications()
            }
        } else {
            _sessionState.value = SessionState.NOT_AUTHENTICATED
        }
    }
    
    fun sendEmailVerification() {
        viewModelScope.launch {
            val result = authRepository.sendEmailVerification()
            _sendVerificationMessage.value = if (result.isSuccess) {
                "📧 Correo de verificación reenviado"
            } else {
                "❌ Error al reenviar: ${result.exceptionOrNull()?.localizedMessage ?: "Intenta de nuevo"}"
            }
        }
    }

    fun refreshVerificationStatus() {
        viewModelScope.launch {
            authRepository.reloadUser()
            isEmailVerified = authRepository.isEmailVerified()
            // 🔒 Cache verification state
            sharedPreferences.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
            if (isEmailVerified) {
                val uid = authRepository.currentUid() ?: return@launch
                userRepository.updateVerificationStatus(uid, true)
            }
        }
    }



    // =====================================================
    // LOAD USER PROFILE
    // =====================================================
    private fun loadUserProfile() {
        val uid = authRepository.currentUid() ?: return

        // Limpia listener previo si existe
        profileJob?.cancel()

        _profileUiState.value = ProfileUiState(isLoading = true)

        profileJob = viewModelScope.launch {
            userRepository.observeUserProfile(uid)
                .catch { e ->
                    if (e is CancellationException) throw e
                    android.util.Log.e("SessionViewModel", "Error observing profile", e)
                }
                .collect { profile ->
                    // 📍 Detect & Notify Location Changes (UX)
                    val oldProfile = _profileUiState.value.profile
                    if (oldProfile != null) {
                        if (oldProfile.commune.isNullOrBlank() && !profile.commune.isNullOrBlank()) {
                             _locationUpdateMessage.value = "📍 Ubicación actualizada: ${profile.commune}"
                        }
                    }

                    _profileUiState.value = ProfileUiState(
                        isLoading = false,
                        profile = profile
                    )
                    
                    // 🚀 Defer non-critical Firestore writes so the UI gets the profile immediately
                    viewModelScope.launch {
                        userRepository.ensureNotificationsActive(profile.uid)
                        userRepository.syncNotificationTopics(profile)
                    }
                    
                    // 📍 AUTO-DETECT LOCATION if missing (User Request)
                    if (profile.commune.isNullOrBlank() && profile.region.isNullOrBlank()) {
                        checkAndAutoUpdateLocation(profile.uid)
                    }
                }
        }
    }


    // =====================================================
    // 🔔 REGISTER & VERIFY FCM TOKEN (Heartbeat)
    // =====================================================
    private fun registerFcmToken() {
        val userId = authRepository.currentUid() ?: return

        viewModelScope.launch {
            // Heartbeat: compares device token vs Firestore token
            // Only writes if they differ → efficient and always in sync
            userRepository.verifyAndSyncFcmToken(userId)
        }
    }

    // =====================================================
    // LOGIN
    // =====================================================
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)

            val result = loginUseCase(email, password)

            if (result.isSuccess) {
                _loginUiState.value = LoginUiState(isLoading = false)
                _sessionState.value = SessionState.AUTHENTICATED
                
                isEmailVerified = result.getOrDefault(false)
                sharedPreferences.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
                isVerificationChecked = true
                
                loadUserProfile()
                registerFcmToken()
            } else {
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Email o contraseña incorrectos"
                )
            }
        }
    }

    // =====================================================
    // BIOMETRIC LOGIN
    // =====================================================
    fun isBiometricLoginAvailable(): Boolean {
        return biometricRepository.isBiometricLoginEnabled()
    }

    fun enableBiometricLogin(email: String, password: String) {
        biometricRepository.setBiometricLoginEnabled(true, email, password)
        _hasBiometricCredentials.value = true
    }

    fun disableBiometricLogin() {
        biometricRepository.clearCredentials()
        _hasBiometricCredentials.value = false
    }

    fun loginWithBiometrics(): Boolean {
        val credentials = biometricRepository.getSavedCredentials()
            ?: return false
        
        val (email, password) = credentials
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)
            
            val result = loginUseCase(email, password)
            
            if (result.isSuccess) {
                _loginUiState.value = LoginUiState(isLoading = false)
                _sessionState.value = SessionState.AUTHENTICATED
                
                isEmailVerified = result.getOrDefault(false)
                sharedPreferences.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
                isVerificationChecked = true
                
                loadUserProfile()
                registerFcmToken()
            } else {
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error de autenticación biométrica"
                )
            }
        }
        return true
    }

    // =====================================================
    // RESET PASSWORD (send email)
    // =====================================================
    fun resetPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Error desconocido"
                onResult(false, error)
            }
        }
    }

    // =====================================================
    // CONFIRM PASSWORD RESET (deep link oobCode)
    // =====================================================
    // null = idle, "LOADING", "SUCCESS", or error message string
    private val _confirmResetState = MutableStateFlow<String?>(null)
    val confirmResetState: StateFlow<String?> = _confirmResetState.asStateFlow()

    fun confirmPasswordReset(oobCode: String, newPassword: String) {
        viewModelScope.launch {
            _confirmResetState.value = "LOADING"
            val result = authRepository.confirmPasswordReset(oobCode, newPassword)
            _confirmResetState.value = if (result.isSuccess) {
                "SUCCESS"
            } else {
                val exception = result.exceptionOrNull() ?: Exception("Error desconocido")
                val error = ErrorMapper.mapPasswordResetError(exception)
                error.message
            }
        }
    }

    fun clearConfirmResetState() {
        _confirmResetState.value = null
    }

    // =====================================================
    // REGISTER
    // =====================================================
    fun register(
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
    ) {
        viewModelScope.launch {
            _registerUiState.value = RegisterUiState(isLoading = true)
            
            val result = registerUseCase(email, password, nickname, birthDate, commune, region, country, latitude, longitude, invitationCode)

            if (result.isSuccess) {
                _sessionState.value = SessionState.AUTHENTICATED
                isEmailVerified = false
                isVerificationChecked = true
                
                sharedPreferences.edit()?.putBoolean("email_verified_cache", false)?.apply()
                
                _registerUiState.value = RegisterUiState(
                    isLoading = false,
                    isSuccess = true
                )
                
                // 🔔 CRITICAL: Sync FCM token and profile on registration
                // Without this, new users never receive push notifications
                loadUserProfile()
                registerFcmToken()
                observeNotifications()
            } else {
                _registerUiState.value = RegisterUiState(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                )
            }
        }
    }

    // =====================================================
    // LOGOUT
    // =====================================================
    fun logout() {
        profileJob?.cancel()
        profileJob = null
        notificationsJob?.cancel()
        notificationsJob = null
        
        _hasBiometricCredentials.value = biometricRepository.isBiometricLoginEnabled()

        // Clear states to prevent crashes in UI observing old data
        _profileUiState.value = ProfileUiState()
        _unreadNotificationsCount.value = 0
        
        // 🔒 Reset verification flags on logout
        isEmailVerified = false
        isVerificationChecked = false
        sharedPreferences.edit()?.putBoolean("email_verified_cache", false)?.apply()

        authRepository.logout()
        _sessionState.value = SessionState.NOT_AUTHENTICATED
    }

    // State for Delete Account UI Feedback
    private val _deleteAccountStatus = MutableStateFlow<String?>(null) // null = idle, "LOADING", "SUCCESS", or error message
    val deleteAccountStatus: StateFlow<String?> = _deleteAccountStatus

    fun resetDeleteAccountStatus() {
        _deleteAccountStatus.value = null
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _deleteAccountStatus.value = "LOADING"
            
            val result = deleteAccountUseCase()
            if (result.isSuccess) {
                _deleteAccountStatus.value = "SUCCESS"
                logout()
            } else {
                _deleteAccountStatus.value = result.exceptionOrNull()?.message ?: "❌ Error desconocido al eliminar cuenta."
            }
        }
    }


    private fun observeNotifications() {
        val uid = authRepository.currentUid() ?: return
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            notificationRepository.observeNotifications(uid)
                .catch { e ->
                    if (e is CancellationException) throw e
                    android.util.Log.e("SessionViewModel", "Error observando notificaciones: ${e.message}")
                }
                .collect { notifications ->
                    _unreadNotificationsCount.value = notifications.count { !it.read }
                }
        }
    }

    fun currentUserId(): String {
        return authRepository.currentUid() ?: ""
    }

    fun updateProfileLocation(latitude: Double, longitude: Double) {
        val uid = authRepository.currentUid() ?: return
        
        viewModelScope.launch {
            try {
                val helper = com.eventos.banana.util.LocationHelper(getApplication())
                val result = helper.detectFromCoordinates(latitude, longitude)
                
                if (result != null) {
                    val commune = result.commune
                    val region = result.region
                    val country = result.country
                    val geohash = GeohashUtils.encode(latitude, longitude, GeohashUtils.getPrecisionForRadius(AppConstants.DEFAULT_SEARCH_RADIUS_KM))
                    
                    // Only update if it's different to save Firestore writes
                    val currentProfile = _profileUiState.value.profile
                    if (currentProfile?.commune != commune || currentProfile.region != region || currentProfile.country != country) {
                        userRepository.updateLocation(uid, region, commune, country, latitude, longitude, geohash)
                        android.util.Log.d("SessionViewModel", "📍 Profile location auto-updated: $commune, $country")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("SessionViewModel", "Error auto-updating location", e)
            }
        }
    }

    private fun checkAndAutoUpdateLocation(uid: String) {
        // Use WorkManager for reliable background execution
        val workRequest = androidx.work.OneTimeWorkRequest.Builder(com.eventos.banana.workers.LocationWorker::class.java)
            .build()
        
        androidx.work.WorkManager.getInstance(getApplication()).enqueue(workRequest)
    }

    init {
        checkSession()
        _hasBiometricCredentials.value = biometricRepository.isBiometricLoginEnabled()
    }
}
