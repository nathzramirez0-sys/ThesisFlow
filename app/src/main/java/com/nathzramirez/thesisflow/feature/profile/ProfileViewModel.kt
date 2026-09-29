package com.nathzramirez.thesisflow.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.profile.UpdateProfileUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val email: String = "",
    val photoUrl: String? = null,
    val displayName: String = "",
    val course: String = "",
    val school: String = "",
    val fieldErrors: Map<Field, ValidationError> = emptyMap(),
    val isSaving: Boolean = false,
    /** One-off: shown once as a snackbar on the profile screen. */
    val saved: Boolean = false,
    val error: DomainError? = null,
)

/** Backs both onboarding and the profile screen: same form, same rules. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val updateProfile: UpdateProfileUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // Prefill once; later edits in the form must not be overwritten by sync updates.
        viewModelScope.launch {
            val user = userRepository.observeCurrentUser().filterNotNull().first()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    email = user.email,
                    photoUrl = user.photoUrl,
                    displayName = user.displayName,
                    course = user.course,
                    school = user.school,
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update {
        it.copy(displayName = value, fieldErrors = it.fieldErrors - Field.DISPLAY_NAME)
    }

    fun onCourseChange(value: String) = _uiState.update {
        it.copy(course = value, fieldErrors = it.fieldErrors - Field.COURSE)
    }

    fun onSchoolChange(value: String) = _uiState.update {
        it.copy(school = value, fieldErrors = it.fieldErrors - Field.SCHOOL)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = updateProfile(state.displayName, state.course, state.school)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, saved = true)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput -> it.copy(isSaving = false, fieldErrors = error.fieldErrors)
                        else -> it.copy(isSaving = false, error = error)
                    }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun messageShown() = _uiState.update { it.copy(saved = false, error = null) }
}
