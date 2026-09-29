package com.nathzramirez.thesisflow.feature.tasks.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskRules
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.task.SaveTaskUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.navigation.TaskEditorRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Everything the user is editing, kept apart from the live member and chapter lists. */
data class TaskForm(
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAt: Instant? = null,
    val chapterId: String? = null,
    val assigneeIds: Set<String> = emptySet(),
)

data class TaskEditorUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    val form: TaskForm = TaskForm(),
    val fieldErrors: Map<Field, ValidationError> = emptyMap(),
    /** Students only: advisers review work and aren't assigned tasks. */
    val assignable: List<Member> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val isDuePickerOpen: Boolean = false,
    val isChapterPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
    /** Set once saved; the screen navigates away. */
    val savedTaskId: String? = null,
) {
    val linkedChapterNumber: Int?
        get() = chapters.indexOfFirst { it.id == form.chapterId }.takeIf { it >= 0 }?.plus(1)
}

private data class LocalState(
    val isLoading: Boolean,
    val form: TaskForm = TaskForm(),
    val fieldErrors: Map<Field, ValidationError> = emptyMap(),
    val isDuePickerOpen: Boolean = false,
    val isChapterPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
    val savedTaskId: String? = null,
)

/** Creates a task, or edits one when the route carries a task id. */
@HiltViewModel(assistedFactory = TaskEditorViewModel.Factory::class)
class TaskEditorViewModel @AssistedInject constructor(
    @Assisted private val route: TaskEditorRoute,
    groupRepository: GroupRepository,
    chapterRepository: ChapterRepository,
    taskRepository: TaskRepository,
    private val saveTask: SaveTaskUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: TaskEditorRoute): TaskEditorViewModel
    }

    private val local = MutableStateFlow(LocalState(isLoading = route.taskId != null))

    init {
        // Prefill once when editing; later sync updates must not overwrite what's being typed.
        if (route.taskId != null) {
            viewModelScope.launch {
                val task = taskRepository.observeTask(route.taskId).filterNotNull().first()
                local.update {
                    it.copy(
                        isLoading = false,
                        form = TaskForm(
                            title = task.title,
                            description = task.description,
                            priority = task.priority,
                            dueAt = task.dueAt,
                            chapterId = task.chapterId,
                            assigneeIds = task.assigneeIds,
                        ),
                    )
                }
            }
        }
    }

    val uiState: StateFlow<TaskEditorUiState> = combine(
        local,
        groupRepository.observeMembers(route.groupId),
        chapterRepository.observeChapters(route.groupId),
    ) { local, members, chapters ->
        TaskEditorUiState(
            isLoading = local.isLoading,
            isEdit = route.taskId != null,
            form = local.form,
            fieldErrors = local.fieldErrors,
            assignable = members.filter(TaskRules::isAssignable),
            chapters = chapters,
            isDuePickerOpen = local.isDuePickerOpen,
            isChapterPickerOpen = local.isChapterPickerOpen,
            isSaving = local.isSaving,
            error = local.error,
            savedTaskId = local.savedTaskId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskEditorUiState())

    fun onTitleChange(value: String) = edit(Field.TASK_TITLE) { it.copy(title = value) }

    fun onDescriptionChange(value: String) = edit(Field.TASK_DESCRIPTION) { it.copy(description = value) }

    fun onPriorityChange(priority: TaskPriority) = edit { it.copy(priority = priority) }

    fun toggleAssignee(uid: String) = edit {
        it.copy(assigneeIds = if (uid in it.assigneeIds) it.assigneeIds - uid else it.assigneeIds + uid)
    }

    fun openDuePicker() = local.update { it.copy(isDuePickerOpen = true) }

    fun dismissDuePicker() = local.update { it.copy(isDuePickerOpen = false) }

    fun onDueChange(dueAt: Instant?) {
        local.update { it.copy(isDuePickerOpen = false) }
        edit { it.copy(dueAt = dueAt) }
    }

    fun openChapterPicker() = local.update { it.copy(isChapterPickerOpen = true) }

    fun dismissChapterPicker() = local.update { it.copy(isChapterPickerOpen = false) }

    fun onChapterChange(chapterId: String?) {
        local.update { it.copy(isChapterPickerOpen = false) }
        edit { it.copy(chapterId = chapterId) }
    }

    fun save() {
        val state = local.value
        if (state.isSaving || state.isLoading) return
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val form = state.form
            val draft = TaskDraft(form.title, form.description, form.priority, form.dueAt, form.chapterId, form.assigneeIds)
            val result = saveTask(route.groupId, route.taskId, draft)
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, savedTaskId = result.value)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput -> it.copy(isSaving = false, fieldErrors = error.fieldErrors)
                        else -> it.copy(isSaving = false, error = error)
                    }
                }
            }
        }
    }

    fun errorShown() = local.update { it.copy(error = null) }

    fun navigationHandled() = local.update { it.copy(savedTaskId = null) }

    private fun edit(clears: Field? = null, change: (TaskForm) -> TaskForm) = local.update {
        it.copy(form = change(it.form), fieldErrors = if (clears != null) it.fieldErrors - clears else it.fieldErrors)
    }
}
