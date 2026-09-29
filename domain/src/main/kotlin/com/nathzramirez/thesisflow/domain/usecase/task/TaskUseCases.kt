package com.nathzramirez.thesisflow.domain.usecase.task

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskRules
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.Validators
import com.nathzramirez.thesisflow.domain.validation.fieldErrorsOf
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Creates a task, or updates it when [taskId] is given. Leader only.
 *
 * Assignees are narrowed to current student members, so a stale selection
 * (someone left while the editor was open) or an adviser never gets assigned.
 */
class SaveTaskUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
) {
    /** Returns the task's id. */
    suspend operator fun invoke(groupId: String, taskId: String?, draft: TaskDraft): AppResult<String> {
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        if (!TaskRules.canManageTasks(role)) return AppResult.Failure(DomainError.PermissionDenied)

        val errors = fieldErrorsOf(
            Field.TASK_TITLE to Validators.taskTitle(draft.title),
            Field.TASK_DESCRIPTION to Validators.taskDescription(draft.description),
        )
        if (errors.isNotEmpty()) return AppResult.Failure(DomainError.InvalidInput(errors))

        val assignable = groupRepository.observeMembers(groupId).first()
            .filter(TaskRules::isAssignable)
            .map { it.uid }
            .toSet()
        val clean = draft.copy(
            title = draft.title.trim(),
            description = draft.description.trim(),
            assigneeIds = draft.assigneeIds intersect assignable,
        )
        return if (taskId == null) {
            taskRepository.createTask(groupId, clean)
        } else {
            when (val result = taskRepository.updateTask(groupId, taskId, clean)) {
                is AppResult.Success -> AppResult.Success(taskId)
                is AppResult.Failure -> result
            }
        }
    }
}

/**
 * Moves a task to another column if the user may: leaders always, members only
 * for tasks assigned to them. Checking here gives a clear message at once, even
 * offline, where a rules rejection would silently undo the change later.
 */
class ChangeTaskStatusUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(groupId: String, taskId: String, status: TaskStatus): AppResult<Unit> {
        val uid = (authRepository.authState.first() as? AuthState.SignedIn)?.uid
            ?: return AppResult.Failure(DomainError.PermissionDenied)
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        val task = taskRepository.observeTask(taskId).first()
            ?: return AppResult.Failure(DomainError.NotFound)

        if (task.status == status) return AppResult.Success(Unit)
        if (!TaskRules.canChangeStatus(role, task, uid)) return AppResult.Failure(DomainError.PermissionDenied)
        return taskRepository.setStatus(groupId, taskId, status)
    }
}

class AddTaskCommentUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(groupId: String, taskId: String, body: String): AppResult<Unit> {
        Validators.comment(body)?.let { error ->
            return AppResult.Failure(DomainError.InvalidInput(mapOf(Field.COMMENT to error)))
        }
        return taskRepository.addComment(groupId, taskId, body.trim())
    }
}
