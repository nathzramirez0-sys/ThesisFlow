package com.nathzramirez.thesisflow.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nathzramirez.thesisflow.data.local.dao.TaskCommentDao
import com.nathzramirez.thesisflow.data.local.dao.TaskDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Comments
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Tasks
import com.nathzramirez.thesisflow.data.remote.awaitOrQueued
import com.nathzramirez.thesisflow.data.remote.requireUid
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes go to Firestore only; the sync manager's listener brings each change
 * back into Room within milliseconds, even offline.
 */
@Singleton
internal class TaskRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val taskDao: TaskDao,
    private val commentDao: TaskCommentDao,
    private val userDao: UserDao,
) : TaskRepository {

    private fun tasks(groupId: String) =
        firestore.collection(Groups.COLLECTION).document(groupId).collection(Tasks.COLLECTION)

    private fun comments(groupId: String, taskId: String) =
        tasks(groupId).document(taskId).collection(Comments.COLLECTION)

    override fun observeTasks(groupId: String): Flow<List<Task>> =
        taskDao.observeForGroup(groupId).map { tasks -> tasks.map { it.toDomain() } }

    override fun observeTask(taskId: String): Flow<Task?> =
        taskDao.observe(taskId).map { it?.toDomain() }

    override fun observeComments(taskId: String): Flow<List<TaskComment>> =
        commentDao.observeForTask(taskId).map { comments -> comments.map { it.toDomain() } }

    override fun observeGroupComments(groupId: String): Flow<List<TaskComment>> =
        commentDao.observeForGroup(groupId).map { comments -> comments.map { it.toDomain() } }

    override suspend fun createTask(groupId: String, draft: TaskDraft): AppResult<String> = safeCall {
        val uid = auth.requireUid()
        val task = tasks(groupId).document()
        task.set(
            draftFields(draft) + mapOf(
                Tasks.GROUP_ID to groupId,
                Tasks.STATUS to TaskStatus.TODO.toWire(),
                Tasks.CREATED_BY to uid,
                Tasks.CREATED_AT to FieldValue.serverTimestamp(),
                Tasks.UPDATED_AT to FieldValue.serverTimestamp(),
                Tasks.UPDATED_BY to uid,
                Tasks.COMPLETED_AT to null,
                Tasks.COMPLETED_BY to null,
            ),
        ).awaitOrQueued()
        task.id
    }

    override suspend fun updateTask(groupId: String, taskId: String, draft: TaskDraft): AppResult<Unit> =
        update(groupId, taskId, draftFields(draft))

    /** Completion is recorded with the status, so the rules can check they agree. */
    override suspend fun setStatus(groupId: String, taskId: String, status: TaskStatus): AppResult<Unit> {
        val done = status == TaskStatus.DONE
        return update(
            groupId,
            taskId,
            mapOf(
                Tasks.STATUS to status.toWire(),
                Tasks.COMPLETED_AT to if (done) FieldValue.serverTimestamp() else null,
                Tasks.COMPLETED_BY to if (done) auth.currentUser?.uid else null,
            ),
        )
    }

    override suspend fun deleteTask(groupId: String, taskId: String): AppResult<Unit> = safeCall {
        tasks(groupId).document(taskId).delete().awaitOrQueued()
    }

    /** The author's name is copied in, so comments read fine even after they leave. */
    override suspend fun addComment(groupId: String, taskId: String, body: String): AppResult<Unit> = safeCall {
        val uid = auth.requireUid()
        comments(groupId, taskId).document().set(
            mapOf(
                Comments.GROUP_ID to groupId,
                Comments.TASK_ID to taskId,
                Comments.BODY to body,
                Comments.AUTHOR_ID to uid,
                Comments.AUTHOR_NAME to userDao.get(uid)?.displayName.orEmpty(),
                Comments.CREATED_AT to FieldValue.serverTimestamp(),
            ),
        ).awaitOrQueued()
    }

    override suspend fun deleteComment(groupId: String, taskId: String, commentId: String): AppResult<Unit> =
        safeCall { comments(groupId, taskId).document(commentId).delete().awaitOrQueued() }

    private fun draftFields(draft: TaskDraft): Map<String, Any?> = mapOf(
        Tasks.TITLE to draft.title,
        Tasks.DESCRIPTION to draft.description,
        Tasks.PRIORITY to draft.priority.toWire(),
        Tasks.DUE_AT to draft.dueAt?.let { Timestamp(it) },
        Tasks.CHAPTER_ID to draft.chapterId,
        // Sorted, so the same set of people always writes the same array.
        Tasks.ASSIGNEE_IDS to draft.assigneeIds.sorted(),
    )

    /** Every edit also records who made it and when; the rules require both. */
    private suspend fun update(groupId: String, taskId: String, changes: Map<String, Any?>): AppResult<Unit> =
        safeCall {
            val uid = auth.requireUid()
            tasks(groupId).document(taskId).update(
                changes + mapOf(
                    Tasks.UPDATED_AT to FieldValue.serverTimestamp(),
                    Tasks.UPDATED_BY to uid,
                ),
            ).awaitOrQueued()
        }
}
