package com.nathzramirez.thesisflow.feature.groups.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.PersonName
import com.nathzramirez.thesisflow.domain.model.ThesisProgress
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GroupSummary(val group: Group, val progress: ThesisProgress)

sealed interface GroupListUiState {
    data object Loading : GroupListUiState
    data class Loaded(val firstName: String, val groups: List<GroupSummary>) : GroupListUiState
}

@HiltViewModel
class GroupListViewModel @Inject constructor(
    groupRepository: GroupRepository,
    chapterRepository: ChapterRepository,
    userRepository: UserRepository,
) : ViewModel() {

    /** Reads Room only, so the list shows instantly and works offline. */
    val uiState: StateFlow<GroupListUiState> = combine(
        groupRepository.observeMyGroups(),
        chapterRepository.observeAllChapters(),
        userRepository.observeCurrentUser(),
    ) { groups, chapters, user ->
        val chaptersByGroup = chapters.groupBy { it.groupId }
        GroupListUiState.Loaded(
            firstName = user?.displayName?.let(PersonName::firstName).orEmpty(),
            groups = groups.map { GroupSummary(it, ThesisProgress.of(chaptersByGroup[it.id].orEmpty())) },
        )
    }
        // Keeps the upstream alive for 5s so a rotation doesn't restart the queries.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupListUiState.Loading)
}
