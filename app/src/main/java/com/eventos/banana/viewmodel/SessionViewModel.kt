package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.LoginUiState
import com.eventos.banana.domain.model.RegisterUiState
import com.eventos.banana.domain.model.ProfileUiState
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.domain.model.UserProfile
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val notificationRepository: com.eventos.banana.data.repository.NotificationRepository = com.eventos.banana.data.repository.NotificationRepository(),
    private var profileListener: ListenerRegistration? = null,
    private var notificationsJob: kotlinx.coroutines.Job? = null,
    private val sharedPreferences: android.content.SharedPreferences? = null
) : ViewModel() {

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
                    sharedPreferences?.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
                    android.util.Log.d("SessionViewModel", "Session check: reload success. Verified: $isEmailVerified")
                    if (isEmailVerified) {
                        val uid = authRepository.currentUid() ?: return@launch
                        userRepository.updateVerificationStatus(uid, true)
                        android.util.Log.d("SessionViewModel", "Session check: isVerified sync success")
                    }
                } catch (e: Exception) {
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
            authRepository.sendEmailVerification()
            // Optionally show a message? For now Fire & Forget
        }
    }

    fun refreshVerificationStatus() {
        viewModelScope.launch {
            authRepository.reloadUser()
            isEmailVerified = authRepository.isEmailVerified()
            // 🔒 Cache verification state
            sharedPreferences?.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
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
        profileListener?.remove()

        _profileUiState.value = ProfileUiState(isLoading = true)

        profileListener = userRepository.listenUserProfile(
            uid = uid,
            onChange = { profile ->
                _profileUiState.value = ProfileUiState(
                    isLoading = false,
                    profile = profile
                )
            },
            onError = {
                android.util.Log.w("SessionViewModel", "Profile not found for $uid, attempting auto-repair")
                // 🔧 AUTO-REPAIR: Create missing profile
                viewModelScope.launch {
                    try {
                        val user = authRepository.getCurrentUser()
                        if (user != null) {
                            val repairedProfile = UserProfile(
                                uid = uid,
                                email = user.email ?: "",
                                nickname = user.email?.substringBefore('@') ?: "Usuario"
                            )
                            
                            android.util.Log.d("SessionViewModel", "Auto-creating profile for $uid with nickname: ${repairedProfile.nickname}")
                            userRepository.createUserProfile(repairedProfile)
                            
                            // Reload after creation
                            _profileUiState.value = ProfileUiState(
                                isLoading = false,
                                profile = repairedProfile
                            )
                            android.util.Log.d("SessionViewModel", "Profile auto-repair successful for $uid")
                        } else {
                            _profileUiState.value = ProfileUiState(
                                isLoading = false,
                                errorMessage = "No se pudo cargar el perfil"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SessionViewModel", "Profile auto-repair failed for $uid", e)
                        _profileUiState.value = ProfileUiState(
                            isLoading = false,
                            errorMessage = "No se pudo cargar el perfil"
                        )
                    }
                }
            }
        )
    }


    // =====================================================
    // 🔔 REGISTER FCM TOKEN (A11.2)
    // =====================================================
    private fun registerFcmToken() {
        val userId = authRepository.currentUid() ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                viewModelScope.launch {
                    userRepository.saveFcmToken(
                        userId = userId,
                        token = token
                    )
                }
            }
    }

    // =====================================================
    // LOGIN
    // =====================================================
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)

            val result = authRepository.login(email, password)

            if (result.isSuccess) {
                _loginUiState.value = LoginUiState(isLoading = false)
                _sessionState.value = SessionState.AUTHENTICATED
                
                // 🆕 Ensure verified status is picked up immediately
                try {
                    authRepository.reloadUser()
                    isEmailVerified = authRepository.isEmailVerified()
                    // 🔒 Cache verification state
                    sharedPreferences?.edit()?.putBoolean("email_verified_cache", isEmailVerified)?.apply()
                    if (isEmailVerified) {
                        val uid = authRepository.currentUid() ?: return@launch
                        userRepository.updateVerificationStatus(uid, true)
                        android.util.Log.d("SessionViewModel", "Login sync SUCCESS")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SessionViewModel", "Login sync failed (non-critical): ${e.message}")
                    isEmailVerified = authRepository.isEmailVerified()
                } finally {
                    // 🔒 Mark verification check as complete after login
                    isVerificationChecked = true
                }
                
                loadUserProfile()
                registerFcmToken()
            } else {
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = "Email o contraseña incorrectos"
                )
            }
        }
    }

    // =====================================================
    // REGISTER
    // =====================================================
    fun register(
        email: String, 
        password: String, 
        nickname: String,
        birthDate: Long,
        commune: String
    ) {
        viewModelScope.launch {
            _registerUiState.value = RegisterUiState(isLoading = true)
            
            // Validar edad +18
            val age = com.eventos.banana.util.AgeCalculator.calculateAge(birthDate)
            if (age < 18) {
                _registerUiState.value = RegisterUiState(
                    isLoading = false,
                    errorMessage = "❌ Debes ser mayor de 18 años para usar esta app"
                )
                return@launch
            }

            val result = authRepository.register(email, password)

            if (result.isSuccess) {
                val uid = authRepository.currentUid() ?: return@launch

                val profile = UserProfile(
                    uid = uid,
                    email = email,
                    nickname = nickname,
                    birthDate = birthDate,
                    age = age,
                    commune = commune,
                    region = com.eventos.banana.data.ChileCommunesList.getRegionForCommune(commune)
                )
                
                android.util.Log.d("SessionViewModel", "Creating profile: uid=$uid, nickname=$nickname, age=$age, commune=$commune")

                try {
                    userRepository.createUserProfile(profile)
                    android.util.Log.d("SessionViewModel", "Profile created successfully for $uid")
                    
                    // 🆕 Send Verification Email Immediately
                    authRepository.sendEmailVerification()
                    
                    // ✅ Logout and mark as success (don't auto-authenticate)
                    authRepository.logout()
                    
                    _registerUiState.value = RegisterUiState(
                        isLoading = false,
                        isSuccess = true
                    )
                    
                } catch (e: Exception) {
                    android.util.Log.e("SessionViewModel", "CRITICAL: Failed to create profile for $uid: ${e.message}", e)
                    // Show error to user
                    _registerUiState.value = RegisterUiState(
                        isLoading = false,
                        errorMessage = "Cuenta creada pero error al guardar perfil: ${e.message}"
                    )
                    return@launch
                }

            } else {
                val exception = result.exceptionOrNull()
                // Map Firebase/Auth errors to friendly messages
                val errorMessage = when {
                    exception?.message?.contains("email address is already in use", ignoreCase = true) == true ->
                        "⚠️ Este correo ya está registrado. Prueba iniciar sesión."
                    exception?.message?.contains("WEAK_PASSWORD", ignoreCase = true) == true || 
                    exception?.message?.contains("Password should be", ignoreCase = true) == true ->
                        "⚠️ La contraseña es muy débil (usa al menos 6 caracteres)."
                    exception?.message?.contains("badly formatted", ignoreCase = true) == true ->
                        "⚠️ El formato del correo es inválido."
                    exception?.message?.contains("network", ignoreCase = true) == true || 
                    exception?.message?.contains("host", ignoreCase = true) == true ->
                        "📡 Error de conexión. Verifica tu internet."
                    else -> "❌ No se pudo crear la cuenta: ${exception?.localizedMessage ?: "Error desconocido"}"
                }

                _registerUiState.value = RegisterUiState(
                    isLoading = false,
                    errorMessage = errorMessage
                )
            }
        }
    }

    // =====================================================
    // LOGOUT
    // =====================================================
    fun logout() {
        profileListener?.remove()
        profileListener = null
        notificationsJob?.cancel()
        notificationsJob = null
        
        // Clear states to prevent crashes in UI observing old data
        _profileUiState.value = ProfileUiState()
        _unreadNotificationsCount.value = 0
        
        // 🔒 Reset verification flags on logout
        isEmailVerified = false
        isVerificationChecked = false
        sharedPreferences?.edit()?.putBoolean("email_verified_cache", false)?.apply()

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
            try {
                // First delete auth account
                val result = authRepository.deleteAccount()
                if (result.isSuccess) {
                    _deleteAccountStatus.value = "SUCCESS"
                    // Then cleanup local session
                    logout()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Error desconocido"
                    android.util.Log.e("SessionViewModel", "Error deleting account: $error")
                    
                    if (error.contains("recent-login", ignoreCase = true) || error.contains("requires-recent-login", ignoreCase = true)) {
                        _deleteAccountStatus.value = "⚠️ Por seguridad, debes cerrar sesión y volver a entrar para eliminar tu cuenta."
                    } else {
                        _deleteAccountStatus.value = "❌ Error: $error"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SessionViewModel", "Error deleting account exception", e)
                _deleteAccountStatus.value = "❌ Error crítico: ${e.message}"
            }
        }
    }


    private fun observeNotifications() {
        val uid = authRepository.currentUid() ?: return
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            notificationRepository.observeNotifications(uid)
                .catch { e ->
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

    init {
        checkSession()
    }
}
