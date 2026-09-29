package com.nathzramirez.thesisflow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.auth.SignUpUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fieldErrors: Map<Field, ValidationError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val error: DomainError? = null,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update {
        it.copy(email = value, fieldErrors = it.fieldErrors - Field.EMAIL)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, fieldErrors = it.fieldErrors - Field.PASSWORD - Field.CONFIRM_PASSWORD)
    }

    fun onConfirmPasswordChange(value: String) = _uiState.update {
        it.copy(confirmPassword = value, fieldErrors = it.fieldErrors - Field.CONFIRM_PASSWORD)
    }

    fun signUp() {
        val state = _uiState.value
        if (state.isSubmitting) return
        submit { signUp(state.email, state.password, state.confirmPassword) }
    }

    fun signUpWithGoogle(idToken: String) {
        if (_uiState.value.isSubmitting) return
        submit { authRepository.signInWithGoogle(idToken) }
    }

    fun onGoogleSignInFailed(error: DomainError) {
        if (error != DomainError.SignInCancelled) _uiState.update { it.copy(error = error) }
    }

    fun errorShown() = _uiState.update { it.copy(error = null) }

    private fun submit(action: suspend () -> AppResult<Unit>) {
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = action()
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput -> it.copy(isSubmitting = false, fieldErrors = error.fieldErrors)
                        else -> it.copy(isSubmitting = false, error = error)
                    }
                }
            }
        }
    }
}
