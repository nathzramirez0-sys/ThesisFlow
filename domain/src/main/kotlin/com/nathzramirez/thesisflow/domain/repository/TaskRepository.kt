package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Task writes work offline: Firestore queues them and they sync on reconnect. */
interface TaskRepository {
    fun observeTasks(groupId: String): Flow<List<Task>>

    fun observeTask(taskId: String): Flow<Task?>

    /** Oldest first, like a chat. */
    fun observeComments(taskId: String): Flow<List<TaskComment>>

    /** Every comment on every task in the group, for the dashboard. */
    fun observeGroupComments(groupId: String): Flow<List<TaskComment>>

    /** Returns the new task's id. */
    suspend fun createTask(groupId: String, draft: TaskDraft): AppResult<String>

    suspend fun updateTask(groupId: String, taskId: String, draft: TaskDraft): AppResult<Unit>

    /** Also records who completed the task and when, or clears that when reopened. */
    suspend fun setStatus(groupId: String, taskId: String, status: TaskStatus): AppResult<Unit>

    /** Deletes the task; a Cloud Function then removes its comments and files. */
    suspend fun deleteTask(groupId: String, taskId: String): AppResult<Unit>

    suspend fun addComment(groupId: String, taskId: String, body: String): AppResult<Unit>

    suspend fun deleteComment(groupId: String, taskId: String, commentId: String): AppResult<Unit>
}
