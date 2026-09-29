package com.nathzramirez.thesisflow.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.repository.ActivityRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.navigation.ActivityFeedRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActivityFeedUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    /** Newest first. */
    val entries: List<ActivityEntry> = emptyList(),
)

/** Everything the phone keeps of the group's activity, newest first. */
@HiltViewModel(assistedFactory = ActivityFeedViewModel.Factory::class)
class ActivityFeedViewModel @AssistedInject constructor(
    @Assisted route: ActivityFeedRoute,
    groupRepository: GroupRepository,
    activityRepository: ActivityRepository,
    chapterRepository: ChapterRepository,
    taskRepository: TaskRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ActivityFeedRoute): ActivityFeedViewModel
    }

    val uiState: StateFlow<ActivityFeedUiState> = combine(
        groupRepository.observeGroup(route.groupId),
        activityRepository.observeRecent(route.groupId),
        chapterRepository.observeChapters(route.groupId),
        taskRepository.observeTasks(route.groupId),
    ) { group, activities, chapters, tasks ->
        ActivityFeedUiState(
            isLoading = group == null,
            groupName = group?.name.orEmpty(),
            entries = linkActivities(activities, chapters, tasks),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityFeedUiState())
}
