package com.nathzramirez.thesisflow.data.remote

import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.FileKind
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus

/*
 * Enums are stored in Firestore as fixed lowercase strings, never as Kotlin's
 * enum names, so renaming a constant in code can't break documents already saved.
 * firestore.rules and the Cloud Functions use the same strings.
 */

internal fun Role.toWire(): String = when (this) {
    Role.LEADER -> "leader"
    Role.MEMBER -> "member"
    Role.ADVISER -> "adviser"
}

internal fun roleFromWire(value: String?): Role? = when (value) {
    "leader" -> Role.LEADER
    "member" -> Role.MEMBER
    "adviser" -> Role.ADVISER
    else -> null
}

internal fun ChapterStatus.toWire(): String = when (this) {
    ChapterStatus.NOT_STARTED -> "not_started"
    ChapterStatus.DRAFTING -> "drafting"
    ChapterStatus.FOR_REVIEW -> "for_review"
    ChapterStatus.REVISIONS -> "revisions"
    ChapterStatus.APPROVED -> "approved"
}

internal fun chapterStatusFromWire(value: String?): ChapterStatus? = when (value) {
    "not_started" -> ChapterStatus.NOT_STARTED
    "drafting" -> ChapterStatus.DRAFTING
    "for_review" -> ChapterStatus.FOR_REVIEW
    "revisions" -> ChapterStatus.REVISIONS
    "approved" -> ChapterStatus.APPROVED
    else -> null
}

internal fun FileKind.toWire(): String = when (this) {
    FileKind.DRAFT -> "draft"
    FileKind.ATTACHMENT -> "attachment"
    FileKind.FEEDBACK -> "feedback"
}

internal fun fileKindFromWire(value: String?): FileKind? = when (value) {
    "draft" -> FileKind.DRAFT
    "attachment" -> FileKind.ATTACHMENT
    "feedback" -> FileKind.FEEDBACK
    else -> null
}

internal fun TaskStatus.toWire(): String = when (this) {
    TaskStatus.TODO -> "todo"
    TaskStatus.IN_PROGRESS -> "in_progress"
    TaskStatus.DONE -> "done"
}

internal fun taskStatusFromWire(value: String?): TaskStatus? = when (value) {
    "todo" -> TaskStatus.TODO
    "in_progress" -> TaskStatus.IN_PROGRESS
    "done" -> TaskStatus.DONE
    else -> null
}

internal fun TaskPriority.toWire(): String = when (this) {
    TaskPriority.LOW -> "low"
    TaskPriority.MEDIUM -> "medium"
    TaskPriority.HIGH -> "high"
}

internal fun taskPriorityFromWire(value: String?): TaskPriority? = when (value) {
    "low" -> TaskPriority.LOW
    "medium" -> TaskPriority.MEDIUM
    "high" -> TaskPriority.HIGH
    else -> null
}

/** Activity entry types, as written by functions/src/activity.ts. */
internal object ActivityTypes {
    const val MEMBER_JOINED = "member_joined"
    const val MEMBER_LEFT = "member_left"
    const val CHAPTER_STATUS = "chapter_status"
    const val DRAFT_UPLOADED = "draft_uploaded"
    const val FEEDBACK_POSTED = "feedback_posted"
    const val FEEDBACK_RESOLVED = "feedback_resolved"
    const val FEEDBACK_REOPENED = "feedback_reopened"
    const val TASK_CREATED = "task_created"
    const val TASK_STATUS = "task_status"
}
