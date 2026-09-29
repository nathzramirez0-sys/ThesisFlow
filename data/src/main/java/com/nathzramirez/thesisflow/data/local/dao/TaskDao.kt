package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nathzramirez.thesisflow.data.local.entity.TaskAssigneeEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskCommentEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskRow
import com.nathzramirez.thesisflow.data.local.entity.TaskWithAssignees
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /** Board order: by column, then soonest due first (undated last), then oldest. */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks WHERE groupId = :groupId
        ORDER BY CASE status WHEN 'TODO' THEN 0 WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END,
                 dueAt IS NULL, dueAt, createdAt
        """,
    )
    fun observeForGroup(groupId: String): Flow<List<TaskWithAssignees>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun observe(taskId: String): Flow<TaskWithAssignees?>

    /** Adds or updates tasks and replaces their assignee rows, for a cached snapshot. */
    @Transaction
    suspend fun upsertAll(rows: List<TaskRow>) {
        if (rows.isEmpty()) return
        upsertTasks(rows.map { it.task })
        deleteAssigneesOf(rows.map { it.task.id })
        insertAssignees(rows.flatMap { it.assignees })
    }

    /** Makes one group's tasks match a complete server snapshot. */
    @Transaction
    suspend fun replaceForGroup(groupId: String, rows: List<TaskRow>) {
        deleteForGroup(groupId)
        deleteAssigneesForGroup(groupId)
        upsertTasks(rows.map { it.task })
        insertAssignees(rows.flatMap { it.assignees })
    }

    @Upsert
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignees(assignees: List<TaskAssigneeEntity>)

    @Query("DELETE FROM task_assignees WHERE taskId IN (:taskIds)")
    suspend fun deleteAssigneesOf(taskIds: List<String>)

    @Query("DELETE FROM task_assignees WHERE groupId = :groupId")
    suspend fun deleteAssigneesForGroup(groupId: String)

    @Query("DELETE FROM tasks WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface TaskCommentDao {
    @Query("SELECT * FROM task_comments WHERE taskId = :taskId ORDER BY createdAt")
    fun observeForTask(taskId: String): Flow<List<TaskCommentEntity>>

    @Upsert
    suspend fun upsertAll(comments: List<TaskCommentEntity>)

    @Transaction
    suspend fun replaceForGroup(groupId: String, comments: List<TaskCommentEntity>) {
        deleteForGroup(groupId)
        upsertAll(comments)
    }

    @Query("DELETE FROM task_comments WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
