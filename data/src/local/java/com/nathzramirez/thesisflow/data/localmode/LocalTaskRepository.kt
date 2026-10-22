package com.nathzramirez.thesisflow.data.localmode

import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.dao.TaskCommentDao
import com.nathzramirez.thesisflow.data.local.dao.TaskDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.TaskAssigneeEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskCommentEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskRow
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

internal class LocalTaskRepository @Inject constructor(
    private val session: LocalSession,
    private val database: ThesisFlowDatabase,
    private val taskDao: TaskDao,
    private val commentDao: TaskCommentDao,
    private val userDao: UserDao,
    private val localModeDao: LocalModeDao,
    private val activity: LocalActivityLog,
    private val cleanup: LocalCleanup,
) : TaskRepository {

    override fun observeTasks(groupId: String): Flow<List<Task>> =
        taskDao.observeForGroup(groupId).map { tasks -> tasks.map { it.toDomain() } }

    override fun observeTask(taskId: String): Flow<Task?> =
        taskDao.observe(taskId).map { it?.toDomain() }

    override fun observeComments(taskId: String): Flow<List<TaskComment>> =
        commentDao.observeForTask(taskId).map { comments -> comments.map { it.toDomain() } }

    override fun observeGroupComments(groupId: String): Flow<List<TaskComment>> =
        commentDao.observeForGroup(groupId).map { comments -> comments.map { it.toDomain() } }

    override suspend fun createTask(groupId: String, draft: TaskDraft): AppResult<String> = localCall {
        val uid = session.requireUid()
        val taskId = newId()
        val task = TaskEntity(
            id = taskId,
            groupId = groupId,
            title = draft.title,
            description = draft.description,
            status = TaskStatus.TODO,
            priority = draft.priority,
            dueAt = draft.dueAt,
            chapterId = draft.chapterId,
            createdBy = uid,
            createdAt = Instant.now(),
            completedAt = null,
            completedBy = null,
            hasPendingWrites = false,
        )
        database.withTransaction {
            taskDao.upsertAll(listOf(TaskRow(task, assigneesOf(task, draft.assigneeIds))))
            activity.record(groupId, ActivityTypes.TASK_CREATED, actorId = uid, targetId = taskId, targetTitle = task.title)
        }
        taskId
    }

    override suspend fun updateTask(groupId: String, taskId: String, draft: TaskDraft): AppResult<Unit> = localCall {
        session.requireUid()
        val existing = localModeDao.task(taskId) ?: throw LocalFailure(DomainError.NotFound)
        val task = existing.task.copy(
            title = draft.title,
            description = draft.description,
            priority = draft.priority,
            dueAt = draft.dueAt,
            chapterId = draft.chapterId,
        )
        taskDao.upsertAll(listOf(TaskRow(task, assigneesOf(task, draft.assigneeIds))))
    }

    /** Done records who finished it and when; moving it back clears both, as in the cloud build. */
    override suspend fun setStatus(groupId: String, taskId: String, status: TaskStatus): AppResult<Unit> = localCall {
        val uid = session.requireUid()
        val existing = localModeDao.task(taskId) ?: throw LocalFailure(DomainError.NotFound)
        if (existing.task.status == status) return@localCall
        val done = status == TaskStatus.DONE
        val task = existing.task.copy(
            status = status,
            completedAt = if (done) Instant.now() else null,
            completedBy = if (done) uid else null,
        )
        database.withTransaction {
            taskDao.upsertAll(listOf(TaskRow(task, existing.assignees)))
            activity.record(
                groupId,
                ActivityTypes.TASK_STATUS,
                actorId = uid,
                targetId = taskId,
                targetTitle = task.title,
                detail = status.toWire(),
            )
        }
    }

    override suspend fun deleteTask(groupId: String, taskId: String): AppResult<Unit> = localCall {
        session.requireUid()
        cleanup.deleteTask(taskId)
    }

    override suspend fun addComment(groupId: String, taskId: String, body: String): AppResult<Unit> = localCall {
        val uid = session.requireUid()
        val authorName = localModeDao.member(groupId, uid)?.displayName ?: userDao.get(uid)?.displayName.orEmpty()
        commentDao.upsertAll(
            listOf(
                TaskCommentEntity(
                    id = newId(),
                    groupId = groupId,
                    taskId = taskId,
                    body = body,
                    authorId = uid,
                    authorName = authorName,
                    createdAt = Instant.now(),
                    hasPendingWrites = false,
                ),
            ),
        )
    }

    override suspend fun deleteComment(groupId: String, taskId: String, commentId: String): AppResult<Unit> =
        localCall {
            session.requireUid()
            localModeDao.deleteComment(commentId)
        }

    private fun assigneesOf(task: TaskEntity, uids: Set<String>) =
        uids.map { TaskAssigneeEntity(taskId = task.id, uid = it, groupId = task.groupId) }
}
