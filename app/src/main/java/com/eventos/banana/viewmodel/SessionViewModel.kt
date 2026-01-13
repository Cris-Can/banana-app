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
import com.google.firebase.firestore.ListenerRegistration

class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private var profileListener: ListenerRegistration? = null

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

    init {
        checkSession()
    }

    // =====================================================
    // SESSION CHECK (APP START)
    // =====================================================
    private fun checkSession() {
        if (authRepository.isUserLoggedIn()) {
            _sessionState.value = SessionState.AUTHENTICATED
            loadUserProfile()
            registerFcmToken()
        } else {
            _sessionState.value = SessionState.NOT_AUTHENTICATED
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
                _profileUiState.value = ProfileUiState(
                    isLoading = false,
                    errorMessage = "No se pudo cargar el perfil"
                )
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
    fun register(email: String, password: String, nickname: String) {
        viewModelScope.launch {
            _registerUiState.value = RegisterUiState(isLoading = true)

            val result = authRepository.register(email, password)

            if (result.isSuccess) {
                val uid = authRepository.currentUid() ?: return@launch

                val profile = UserProfile(
                    uid = uid,
                    email = email,
                    nickname = nickname
                )

                userRepository.createUserProfile(profile)

                _profileUiState.value = ProfileUiState(
                    isLoading = false,
                    profile = profile
                )

                _registerUiState.value = RegisterUiState(isLoading = false)
                _sessionState.value = SessionState.AUTHENTICATED
                registerFcmToken()

            } else {
                _registerUiState.value = RegisterUiState(
                    isLoading = false,
                    errorMessage = "No se pudo crear la cuenta"
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

        authRepository.logout()
        _sessionState.value = SessionState.NOT_AUTHENTICATED
    }


    fun currentUserId(): String {
        return authRepository.currentUid() ?: ""
    }
}
