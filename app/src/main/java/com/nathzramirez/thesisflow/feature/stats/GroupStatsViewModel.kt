package com.nathzramirez.thesisflow.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.GroupStats
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.usecase.stats.ObserveGroupStatsUseCase
import com.nathzramirez.thesisflow.navigation.GroupStatsRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class GroupStatsUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val stats: GroupStats? = null,
)

/** Everything comes from the local cache, so the dashboard opens instantly and offline. */
@HiltViewModel(assistedFactory = GroupStatsViewModel.Factory::class)
class GroupStatsViewModel @AssistedInject constructor(
    @Assisted route: GroupStatsRoute,
    groupRepository: GroupRepository,
    observeStats: ObserveGroupStatsUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: GroupStatsRoute): GroupStatsViewModel
    }

    val uiState: StateFlow<GroupStatsUiState> = combine(
        groupRepository.observeGroup(route.groupId),
        observeStats(route.groupId, ZoneId.systemDefault()),
    ) { group, stats ->
        GroupStatsUiState(isLoading = group == null, groupName = group?.name.orEmpty(), stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupStatsUiState())
}
