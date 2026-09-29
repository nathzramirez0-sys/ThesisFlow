package com.nathzramirez.thesisflow.feature.tasks.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskRules
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.task.ChangeTaskStatusUseCase
import com.nathzramirez.thesisflow.feature.tasks.afterAdvance
import com.nathzramirez.thesisflow.navigation.TaskBoardRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskBoardView { LIST, BOARD }

enum class TaskFilter { ALL, MINE }

data class TaskBoardUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val role: Role? = null,
    val myUid: String? = null,
    /** After the filter is applied, in board order. */
    val tasks: List<Task> = emptyList(),
    val totalTasks: Int = 0,
    val members: Map<String, Member> = emptyMap(),
    /** Chapter id to its position in the chapter list, for "Ch 02" labels. */
    val chapterNumbers: Map<String, Int> = emptyMap(),
    val view: TaskBoardView = TaskBoardView.LIST,
    val filter: TaskFilter = TaskFilter.ALL,
    val error: DomainError? = null,
) {
    val canCreate: Boolean get() = role?.let(TaskRules::canManageTasks) == true

    fun inColumn(status: TaskStatus): List<Task> = tasks.filter { it.status == status }

    fun assigneesOf(task: Task): List<Member> = task.assigneeIds.mapNotNull(members::get)

    fun canAdvance(task: Task): Boolean =
        role != null && myUid != null && TaskRules.canChangeStatus(role, task, myUid)
}

private data class BoardData(
    val groupName: String?,
    val role: Role?,
    val myUid: String?,
    val tasks: List<Task>,
    val members: Map<String, Member>,
    val chapterNumbers: Map<String, Int>,
)

private data class LocalState(
    val view: TaskBoardView = TaskBoardView.LIST,
    val filter: TaskFilter = TaskFilter.ALL,
    val error: DomainError? = null,
)

/** Receives its route through assisted injection, so tests can pass a plain [TaskBoardRoute]. */
@HiltViewModel(assistedFactory = TaskBoardViewModel.Factory::class)
class TaskBoardViewModel @AssistedInject constructor(
    @Assisted private val route: TaskBoardRoute,
    authRepository: AuthRepository,
    groupRepository: GroupRepository,
    chapterRepository: ChapterRepository,
    taskRepository: TaskRepository,
    private val changeTaskStatus: ChangeTaskStatusUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: TaskBoardRoute): TaskBoardViewModel
    }

    private val local = MutableStateFlow(LocalState())

    private val data = combine(
        groupRepository.observeGroup(route.groupId),
        authRepository.authState.map { (it as? AuthState.SignedIn)?.uid },
        taskRepository.observeTasks(route.groupId),
        groupRepository.observeMembers(route.groupId),
        chapterRepository.observeChapters(route.groupId),
    ) { group, uid, tasks, members, chapters ->
        BoardData(
            groupName = group?.name,
            role = group?.myRole,
            myUid = uid,
            tasks = tasks,
            members = members.associateBy { it.uid },
            chapterNumbers = chapters.mapIndexed { index, chapter -> chapter.id to index + 1 }.toMap(),
        )
    }

    val uiState: StateFlow<TaskBoardUiState> = combine(data, local) { data, local ->
        val visible = when (local.filter) {
            TaskFilter.ALL -> data.tasks
            TaskFilter.MINE -> data.tasks.filter { data.myUid != null && data.myUid in it.assigneeIds }
        }
        TaskBoardUiState(
            isLoading = data.groupName == null,
            groupName = data.groupName.orEmpty(),
            role = data.role,
            myUid = data.myUid,
            tasks = visible,
            totalTasks = data.tasks.size,
            members = data.members,
            chapterNumbers = data.chapterNumbers,
            view = local.view,
            filter = local.filter,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskBoardUiState())

    fun setView(view: TaskBoardView) = local.update { it.copy(view = view) }

    fun setFilter(filter: TaskFilter) = local.update { it.copy(filter = filter) }

    /** Start → Mark done → Reopen, depending on where the task is now. */
    fun advance(task: Task) {
        viewModelScope.launch {
            val result = changeTaskStatus(route.groupId, task.id, task.status.afterAdvance())
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }

    fun errorShown() = local.update { it.copy(error = null) }
}
