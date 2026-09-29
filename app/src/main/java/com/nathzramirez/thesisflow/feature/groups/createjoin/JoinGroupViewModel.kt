package com.nathzramirez.thesisflow.feature.groups.createjoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.JoinGroupUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinGroupUiState(
    val code: String = "",
    val codeError: ValidationError? = null,
    val isSubmitting: Boolean = false,
    val error: DomainError? = null,
    val joinedGroupId: String? = null,
)

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val joinGroup: JoinGroupUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinGroupUiState())
    val uiState: StateFlow<JoinGroupUiState> = _uiState.asStateFlow()

    /** Fills in a code from an invite link, without overwriting what the user typed. */
    fun prefill(code: String) = _uiState.update { if (it.code.isEmpty()) it.copy(code = code) else it }

    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value.uppercase(), codeError = null) }

    fun join() {
        val state = _uiState.value
        if (state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = joinGroup(state.code)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false, joinedGroupId = result.value)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput ->
                            it.copy(isSubmitting = false, codeError = error.fieldErrors[Field.INVITE_CODE])
                        else -> it.copy(isSubmitting = false, error = error)
                    }
                }
            }
        }
    }

    fun errorShown() = _uiState.update { it.copy(error = null) }

    fun navigationHandled() = _uiState.update { it.copy(joinedGroupId = null) }
}
