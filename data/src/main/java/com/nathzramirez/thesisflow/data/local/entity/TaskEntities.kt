package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import java.time.Instant

@Entity(tableName = "tasks", indices = [Index("groupId"), Index("chapterId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val chapterId: String?,
    val createdBy: String,
    val createdAt: Instant?,
    val completedAt: Instant?,
    val completedBy: String?,
    val hasPendingWrites: Boolean,
)

/**
 * One row per (task, assignee). Firestore stores assignees as an array on the
 * task; a join table lets SQL answer "my tasks" and "done per member" directly.
 */
@Entity(
    tableName = "task_assignees",
    primaryKeys = ["taskId", "uid"],
    indices = [Index("uid"), Index("groupId")],
)
data class TaskAssigneeEntity(
    val taskId: String,
    val uid: String,
    val groupId: String,
)

@Entity(tableName = "task_comments", indices = [Index("taskId"), Index("groupId")])
data class TaskCommentEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val taskId: String,
    val body: String,
    val authorId: String,
    val authorName: String,
    val createdAt: Instant?,
    val hasPendingWrites: Boolean,
)

/** A synced task document split into its table rows. */
data class TaskRow(val task: TaskEntity, val assignees: List<TaskAssigneeEntity>)

data class TaskWithAssignees(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val assignees: List<TaskAssigneeEntity>,
)
