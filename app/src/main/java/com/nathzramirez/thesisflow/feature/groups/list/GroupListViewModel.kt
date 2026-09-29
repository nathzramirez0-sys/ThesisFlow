package com.nathzramirez.thesisflow.feature.groups.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface GroupListUiState {
    data object Loading : GroupListUiState
    data class Loaded(val groups: List<Group>) : GroupListUiState
}

@HiltViewModel
class GroupListViewModel @Inject constructor(
    groupRepository: GroupRepository,
) : ViewModel() {

    /** Reads Room only, so the list shows instantly and works offline. */
    val uiState: StateFlow<GroupListUiState> = groupRepository.observeMyGroups()
        .map { GroupListUiState.Loaded(it) }
        // Keeps the upstream alive for 5s so a rotation doesn't restart the query.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupListUiState.Loading)
}
