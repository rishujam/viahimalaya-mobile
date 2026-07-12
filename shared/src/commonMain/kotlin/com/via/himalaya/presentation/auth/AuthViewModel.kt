package com.via.himalaya.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.domain.model.UserProfile
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.util.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileScreenUIState())
    val state: StateFlow<ProfileScreenUIState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            user?.let {
                _state.update {
                    it.copy(
                        initialAuthCheckRunning = false,
                        userProfile = user
                    )
                }
            } ?: run {
                _state.update {
                    it.copy(
                        initialAuthCheckRunning = false
                    )
                }
            }
        }
    }

    /** Called with the Google ID token retrieved by the platform sign-in UI. */
    fun onGoogleIdToken(idToken: String) = viewModelScope.launch {
        _state.update {
            it.copy(
                isLoading = true
            )
        }
        when (val result = authRepository.signInWithGoogle(idToken)) {
            is Result.Success -> {
                val user = result.data
                println("AuthViewModel: Sign-in successful!: ${user?.email}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        userProfile = user
                    )
                }
            }
            is Result.Error -> {
                _state.update { it.copy(isLoading = false) }
                println("AuthViewModel: Sign-in failed: ${result.message}")
            }
            is Result.Loading -> _state.update { it.copy(isLoading = true) }
        }
    }

    /** Reports a platform-side failure (e.g. user cancelled the Google chooser). */
    fun onSignInFailed(message: String) {
        println("AuthViewModel: Sign-in failed: $message")
        _state.update { it.copy(isLoading = false) }
    }

    fun signOut() = viewModelScope.launch {
        val user = authRepository.getCurrentUser()
        println("AuthViewModel: Signing out user: $user")
        authRepository.signOut()
        _state.update { it.copy(userProfile = null) }
        println("AuthViewModel: User signed out successfully")
    }
}
