package com.nathzramirez.thesisflow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.auth.SendPasswordResetUseCase
import com.nathzramirez.thesisflow.domain.usecase.auth.SignInUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The "forgot password" dialog; null while it is closed. */
data class PasswordResetState(
    val email: String,
    val error: ValidationError? = null,
    val isSending: Boolean = false,
)

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val fieldErrors: Map<Field, ValidationError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val passwordReset: PasswordResetState? = null,
    /** One-off messages; the screen shows them once, then calls [LoginViewModel.messageShown]. */
    val error: DomainError? = null,
    val resetLinkSentTo: String? = null,
)

/**
 * Success needs no navigation here: signing in changes the session state, and
 * the root switches to onboarding or the main app on its own.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val sendPasswordReset: SendPasswordResetUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update {
        it.copy(email = value, fieldErrors = it.fieldErrors - Field.EMAIL)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, fieldErrors = it.fieldErrors - Field.PASSWORD)
    }

    fun signIn() {
        val state = _uiState.value
        if (state.isSubmitting) return
        submit { signIn(state.email, state.password) }
    }

    fun signInWithGoogle(idToken: String) {
        if (_uiState.value.isSubmitting) return
        submit { authRepository.signInWithGoogle(idToken) }
    }

    /** Closing the Google sheet on purpose isn't an error worth showing. */
    fun onGoogleSignInFailed(error: DomainError) {
        if (error != DomainError.SignInCancelled) _uiState.update { it.copy(error = error) }
    }

    fun openPasswordReset() = _uiState.update {
        it.copy(passwordReset = PasswordResetState(email = it.email))
    }

    fun onResetEmailChange(value: String) = _uiState.update {
        it.copy(passwordReset = it.passwordReset?.copy(email = value, error = null))
    }

    fun dismissPasswordReset() = _uiState.update { it.copy(passwordReset = null) }

    fun sendResetLink() {
        val reset = _uiState.value.passwordReset ?: return
        if (reset.isSending) return
        _uiState.update { it.copy(passwordReset = reset.copy(isSending = true)) }
        viewModelScope.launch {
            when (val result = sendPasswordReset(reset.email)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(passwordReset = null, resetLinkSentTo = reset.email.trim())
                }
                is AppResult.Failure -> _uiState.update {
                    val fieldError = (result.error as? DomainError.InvalidInput)?.fieldErrors?.get(Field.EMAIL)
                    if (fieldError != null) {
                        it.copy(passwordReset = reset.copy(isSending = false, error = fieldError))
                    } else {
                        it.copy(passwordReset = reset.copy(isSending = false), error = result.error)
                    }
                }
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(error = null, resetLinkSentTo = null) }

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
