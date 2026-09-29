package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

/** The three kanban columns, in board order. */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    /** The column a task moves to when advanced; null once it is done. */
    val next: TaskStatus?
        get() = when (this) {
            TODO -> IN_PROGRESS
            IN_PROGRESS -> DONE
            DONE -> null
        }
}

enum class TaskPriority { LOW, MEDIUM, HIGH }

data class Task(
    val id: String,
    val groupId: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val chapterId: String?,
    val assigneeIds: Set<String>,
    val createdBy: String,
    val createdAt: Instant?,
    val completedAt: Instant?,
    /** True while a local edit hasn't reached the server yet, e.g. offline. */
    val hasPendingWrites: Boolean,
) {
    fun isOverdue(now: Instant): Boolean =
        dueAt != null && status != TaskStatus.DONE && now.isAfter(dueAt)
}

/** What the task editor produces, for both new and existing tasks. */
data class TaskDraft(
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val dueAt: Instant?,
    val chapterId: String?,
    val assigneeIds: Set<String>,
)

data class TaskComment(
    val id: String,
    val taskId: String,
    val body: String,
    val authorId: String,
    val authorName: String,
    val createdAt: Instant?,
    val hasPendingWrites: Boolean,
)

/**
 * Who may do what with tasks. firestore.rules enforces the same table on the
 * server; this copy lets the UI offer only allowed actions.
 */
object TaskRules {

    /** Creating, editing, assigning and deleting tasks. */
    fun canManageTasks(role: Role): Boolean = role == Role.LEADER

    /** Leaders move any task; members move only tasks assigned to them. */
    fun canChangeStatus(role: Role, task: Task, uid: String): Boolean = when (role) {
        Role.LEADER -> true
        Role.MEMBER -> uid in task.assigneeIds
        Role.ADVISER -> false
    }

    /** Everyone in the group can discuss a task, the adviser included. */
    fun canComment(role: Role): Boolean = true

    /** Advisers' files go on chapter feedback instead. */
    fun canAttachFiles(role: Role): Boolean = role != Role.ADVISER

    /** Advisers review; tasks are work for students. */
    fun isAssignable(member: Member): Boolean = member.role != Role.ADVISER
}
