package com.nathzramirez.thesisflow.feature.groups.createjoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.CreateGroupUseCase
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

data class CreateGroupUiState(
    val name: String = "",
    val thesisTitle: String = "",
    val course: String = "",
    val school: String = "",
    val fieldErrors: Map<Field, ValidationError> = emptyMap(),
    val isSubmitting: Boolean = false,
    val error: DomainError? = null,
    /** Set once the group exists; the screen navigates to it. */
    val createdGroupId: String? = null,
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val createGroup: CreateGroupUseCase,
    userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    init {
        // Groupmates usually share a course and school, so start from the creator's.
        viewModelScope.launch {
            val user = userRepository.observeCurrentUser().filterNotNull().first()
            _uiState.update {
                it.copy(course = it.course.ifEmpty { user.course }, school = it.school.ifEmpty { user.school })
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update {
        it.copy(name = value, fieldErrors = it.fieldErrors - Field.GROUP_NAME)
    }

    fun onThesisTitleChange(value: String) = _uiState.update {
        it.copy(thesisTitle = value, fieldErrors = it.fieldErrors - Field.THESIS_TITLE)
    }

    fun onCourseChange(value: String) = _uiState.update {
        it.copy(course = value, fieldErrors = it.fieldErrors - Field.COURSE)
    }

    fun onSchoolChange(value: String) = _uiState.update {
        it.copy(school = value, fieldErrors = it.fieldErrors - Field.SCHOOL)
    }

    fun create() {
        val state = _uiState.value
        if (state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = createGroup(state.name, state.thesisTitle, state.course, state.school)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false, createdGroupId = result.value)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput -> it.copy(isSubmitting = false, fieldErrors = error.fieldErrors)
                        else -> it.copy(isSubmitting = false, error = error)
                    }
                }
            }
        }
    }

    fun errorShown() = _uiState.update { it.copy(error = null) }

    fun navigationHandled() = _uiState.update { it.copy(createdGroupId = null) }
}
