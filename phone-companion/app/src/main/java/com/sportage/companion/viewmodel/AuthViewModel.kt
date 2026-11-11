package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.companion.data.auth.AuthRepository
import com.sportage.companion.data.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val user: UserSession? = null,
    val error: String? = null,
    val resetSent: Boolean = false
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.currentUser.collect { u ->
                _state.value = _state.value.copy(user = u)
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            auth.signIn(email, password)
                .onSuccess { _state.value = _state.value.copy(loading = false, user = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            auth.signInWithGoogle(idToken)
                .onSuccess { _state.value = _state.value.copy(loading = false, user = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            auth.signUp(email, password)
                .onSuccess { _state.value = _state.value.copy(loading = false, user = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, resetSent = false)
            auth.sendPasswordReset(email)
                .onSuccess { _state.value = _state.value.copy(loading = false, resetSent = true) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun signOut() = auth.signOut()

    fun clearError() { _state.value = _state.value.copy(error = null) }
}