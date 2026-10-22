package com.nathzramirez.thesisflow.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.Person
import com.nathzramirez.thesisflow.domain.repository.PeopleRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.result.onFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PeopleUiState(
    /** Null until the list has loaded, so the sample offer doesn't flash on a phone that has people. */
    val people: List<Person>? = null,
    val isBusy: Boolean = false,
    val error: DomainError? = null,
)

/**
 * The local build's stand-in for sign-in. Picking, adding or sampling a person
 * changes who is signed in; MainViewModel then moves the app on by itself.
 */
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
) : ViewModel() {

    private val isBusy = MutableStateFlow(false)
    private val error = MutableStateFlow<DomainError?>(null)

    val uiState: StateFlow<PeopleUiState> =
        combine(peopleRepository.observePeople(), isBusy, error) { people, busy, error ->
            PeopleUiState(people = people, isBusy = busy, error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    fun choose(uid: String) = run { peopleRepository.switchTo(uid) }

    fun addPerson() = run { peopleRepository.addPerson() }

    fun addSampleGroup() = run { peopleRepository.addSampleGroup() }

    fun errorShown() {
        error.value = null
    }

    /** One action at a time, so a double tap can't add two people. */
    private fun run(action: suspend () -> AppResult<Unit>) {
        if (isBusy.value) return
        isBusy.value = true
        viewModelScope.launch {
            action().onFailure { error.value = it }
            isBusy.value = false
        }
    }
}
