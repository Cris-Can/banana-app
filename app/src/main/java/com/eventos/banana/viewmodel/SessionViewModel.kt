package com.eventos.banana.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.AuthRepository
import com.eventos.banana.data.repository.UserRepository
import com.eventos.banana.domain.model.LoginUiState
import com.eventos.banana.domain.model.SessionState
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.domain.model.RegisterUiState
import com.eventos.banana.domain.model.ProfileUiState

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    // ---------- UI STATE (LOGIN UX) ----------
    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState
    private val _registerUiState = MutableStateFlow(RegisterUiState())
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState

    // ---------- UI STATE (PROFILE) ----------
    private val _profileUiState = MutableStateFlow(ProfileUiState())
    val profileUiState: StateFlow<ProfileUiState> = _profileUiState


    // ---------- SESSION STATE (NAVIGATION) ----------
    private val _sessionState = MutableStateFlow(SessionState.LOADING)
    val sessionState: StateFlow<SessionState> = _sessionState

    init {
        checkSession()
    }

    // ---------- SESSION CHECK ----------
    private fun checkSession() {
        if (authRepository.isUserLoggedIn()) {
            _sessionState.value = SessionState.AUTHENTICATED
            loadUserProfile() // 👈 AÑADIR ESTA LÍNEA
        } else {
            _sessionState.value = SessionState.NOT_AUTHENTICATED
        }
    }


    // ---------- LOAD USER PROFILE ----------
    private fun loadUserProfile() {
        viewModelScope.launch {
            _profileUiState.value = ProfileUiState(isLoading = true)

            val uid = authRepository.currentUid()
            if (uid == null) {
                _profileUiState.value = ProfileUiState(
                    isLoading = false,
                    errorMessage = "Usuario no autenticado"
                )
                return@launch
            }

            try {
                val profile = userRepository.getUserProfile(uid)

                if (profile != null) {
                    _profileUiState.value = ProfileUiState(
                        isLoading = false,
                        profile = profile
                    )
                } else {
                    // ✅ AUTO-CREACIÓN DE PERFIL
                    val email = authRepository.currentUserEmail() ?: ""

                    val newProfile = UserProfile(
                        uid = uid,
                        email = email,
                        nickname = "Usuario"
                    )

                    userRepository.createUserProfile(newProfile)

                    _profileUiState.value = ProfileUiState(
                        isLoading = false,
                        profile = newProfile
                    )
                }

            } catch (e: Exception) {
                _profileUiState.value = ProfileUiState(
                    isLoading = false,
                    errorMessage = "No se pudo cargar el perfil (sin conexión)"
                )
            }
        }
    }




    // ---------- LOGIN ----------
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)

            val result = authRepository.login(email, password)

            if (result.isSuccess) {
                _loginUiState.value = LoginUiState(isLoading = false)
                _sessionState.value = SessionState.AUTHENTICATED
                loadUserProfile() // 👈 AÑADIR
            }
            else {
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = "Email o contraseña incorrectos"
                )
            }
        }
    }

    // ---------- REGISTER ----------
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

            } else {
                _registerUiState.value = RegisterUiState(
                    isLoading = false,
                    errorMessage = "No se pudo crear la cuenta. Revisa los datos."
                )
            }
        }
    }


    // ---------- LOGOUT ----------
    fun logout() {
        authRepository.logout()
        _sessionState.value = SessionState.NOT_AUTHENTICATED
    }
    fun currentUserId(): String {
        return authRepository.currentUid() ?: ""
    }

}
