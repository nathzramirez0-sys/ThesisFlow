package com.nathzramirez.thesisflow.feature.tasks.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.TaskRules
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.file.QueueUploadUseCase
import com.nathzramirez.thesisflow.domain.usecase.task.AddTaskCommentUseCase
import com.nathzramirez.thesisflow.domain.usecase.task.ChangeTaskStatusUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.feature.chapters.detail.FileToOpen
import com.nathzramirez.thesisflow.navigation.TaskDetailRoute
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

/** The linked chapter with its position, so the screen can show "Chapter 02" and open it. */
data class LinkedChapter(val chapter: Chapter, val number: Int)

data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val task: Task? = null,
    val role: Role? = null,
    val myUid: String? = null,
    val members: Map<String, Member> = emptyMap(),
    val chapter: LinkedChapter? = null,
    val comments: List<TaskComment> = emptyList(),
    val attachments: List<FileAttachment> = emptyList(),
    val uploads: List<PendingUpload> = emptyList(),
    val commentDraft: String = "",
    val commentError: ValidationError? = null,
    val isSendingComment: Boolean = false,
    val openingFileId: String? = null,
    val fileToOpen: FileToOpen? = null,
    val confirmDelete: Boolean = false,
    val error: DomainError? = null,
    val isClosed: Boolean = false,
) {
    val canManage: Boolean get() = role?.let(TaskRules::canManageTasks) == true
    val canChangeStatus: Boolean
        get() = task != null && role != null && myUid != null && TaskRules.canChangeStatus(role, task, myUid)
    val canAttach: Boolean get() = role?.let(TaskRules::canAttachFiles) == true

    /** Authors can delete their own comments; leaders can delete any. */
    fun canDelete(comment: TaskComment): Boolean = comment.authorId == myUid || role == Role.LEADER
}

private data class TaskData(
    val task: Task?,
    val role: Role?,
    val myUid: String?,
    val members: Map<String, Member>,
    val chapter: LinkedChapter?,
)

private data class FileData(
    val comments: List<TaskComment>,
    val attachments: List<FileAttachment>,
    val uploads: List<PendingUpload>,
)

private data class LocalState(
    val commentDraft: String = "",
    val commentError: ValidationError? = null,
    val isSendingComment: Boolean = false,
    val openingFileId: String? = null,
    val fileToOpen: FileToOpen? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val error: DomainError? = null,
)

/** Receives its route through assisted injection, so tests can pass a plain [TaskDetailRoute]. */
@HiltViewModel(assistedFactory = TaskDetailViewModel.Factory::class)
class TaskDetailViewModel @AssistedInject constructor(
    @Assisted private val route: TaskDetailRoute,
    authRepository: AuthRepository,
    groupRepository: GroupRepository,
    chapterRepository: ChapterRepository,
    private val taskRepository: TaskRepository,
    private val fileRepository: FileRepository,
    private val changeTaskStatus: ChangeTaskStatusUseCase,
    private val addComment: AddTaskCommentUseCase,
    private val queueUpload: QueueUploadUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: TaskDetailRoute): TaskDetailViewModel
    }

    private val local = MutableStateFlow(LocalState())

    /** Distinguishes "still loading" from "deleted while open". */
    private var hasSeenTask = false

    private val taskData = combine(
        taskRepository.observeTask(route.taskId),
        groupRepository.observeGroup(route.groupId),
        authRepository.authState.map { (it as? AuthState.SignedIn)?.uid },
        groupRepository.observeMembers(route.groupId),
        chapterRepository.observeChapters(route.groupId),
    ) { task, group, uid, members, chapters ->
        val index = chapters.indexOfFirst { it.id == task?.chapterId }
        TaskData(
            task = task,
            role = group?.myRole,
            myUid = uid,
            members = members.associateBy { it.uid },
            chapter = if (index >= 0) LinkedChapter(chapters[index], index + 1) else null,
        )
    }

    private val fileData = combine(
        taskRepository.observeComments(route.taskId),
        fileRepository.observeTaskAttachments(route.taskId),
        fileRepository.observePendingForTask(route.taskId),
    ) { comments, attachments, uploads -> FileData(comments, attachments, uploads) }

    val uiState: StateFlow<TaskDetailUiState> = combine(taskData, fileData, local) { data, files, local ->
        if (data.task != null) hasSeenTask = true
        TaskDetailUiState(
            isLoading = data.task == null && !hasSeenTask,
            task = data.task,
            role = data.role,
            myUid = data.myUid,
            members = data.members,
            chapter = data.chapter,
            comments = files.comments,
            attachments = files.attachments,
            uploads = files.uploads,
            commentDraft = local.commentDraft,
            commentError = local.commentError,
            isSendingComment = local.isSendingComment,
            openingFileId = local.openingFileId,
            fileToOpen = local.fileToOpen,
            confirmDelete = local.confirmDelete,
            error = local.error,
            isClosed = local.deleted || (data.task == null && hasSeenTask),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    fun changeStatus(status: TaskStatus) {
        viewModelScope.launch {
            val result = changeTaskStatus(route.groupId, route.taskId, status)
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }

    fun onCommentChange(value: String) = local.update { it.copy(commentDraft = value, commentError = null) }

    fun sendComment() {
        val state = local.value
        if (state.isSendingComment) return
        local.update { it.copy(isSendingComment = true) }
        viewModelScope.launch {
            val result = addComment(route.groupId, route.taskId, state.commentDraft)
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSendingComment = false, commentDraft = "")
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput ->
                            it.copy(isSendingComment = false, commentError = error.fieldErrors[Field.COMMENT])
                        else -> it.copy(isSendingComment = false, error = error)
                    }
                }
            }
        }
    }

    fun deleteComment(comment: TaskComment) {
        viewModelScope.launch {
            val result = taskRepository.deleteComment(route.groupId, route.taskId, comment.id)
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }

    /** Attachments need no note, so a picked file is queued straight away. */
    fun onFilePicked(sourceUri: String) {
        viewModelScope.launch {
            val result = queueUpload(route.groupId, UploadTarget.TaskAttachment(route.taskId), sourceUri)
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }

    fun retryUpload(fileId: String) = viewModelScope.launch { fileRepository.retryUpload(fileId) }

    fun discardUpload(fileId: String) = viewModelScope.launch { fileRepository.discardUpload(fileId) }

    fun openFile(file: FileAttachment) {
        if (local.value.openingFileId != null) return
        local.update { it.copy(openingFileId = file.id) }
        viewModelScope.launch {
            val result = fileRepository.localCopy(file)
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(openingFileId = null, fileToOpen = FileToOpen(result.value, file.mimeType))
                    is AppResult.Failure -> it.copy(openingFileId = null, error = result.error)
                }
            }
        }
    }

    fun fileOpened() = local.update { it.copy(fileToOpen = null) }

    fun askDelete() = local.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = local.update { it.copy(confirmDelete = false) }

    fun delete() {
        local.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val result = taskRepository.deleteTask(route.groupId, route.taskId)) {
                is AppResult.Success -> local.update { it.copy(deleted = true) }
                is AppResult.Failure -> local.update { it.copy(error = result.error) }
            }
        }
    }

    fun errorShown() = local.update { it.copy(error = null) }
}
