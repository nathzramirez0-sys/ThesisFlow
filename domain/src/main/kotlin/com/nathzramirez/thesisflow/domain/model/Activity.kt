package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

/**
 * Something that happened in a group. Entries are written by Cloud Functions
 * when the change reaches the server, never by the app, so nobody can forge one.
 */
data class Activity(
    val id: String,
    val groupId: String,
    val actorId: String,
    /** Copied when the entry was written, so it reads right after the person leaves. */
    val actorName: String,
    val event: ActivityEvent,
    val createdAt: Instant?,
)

/**
 * What happened. Titles are copies from the moment it happened, like any log.
 * Entry types this version doesn't know are skipped when read, so a newer
 * server never breaks an older app.
 */
sealed interface ActivityEvent {

    /** Events about a chapter, which the feed links to. */
    sealed interface ChapterEvent : ActivityEvent {
        val chapterId: String
        val chapterTitle: String
    }

    /** Events about a task, which the feed links to. */
    sealed interface TaskEvent : ActivityEvent {
        val taskId: String
        val taskTitle: String
    }

    data class MemberJoined(val role: Role) : ActivityEvent

    /** Left, or was removed by a leader; the server can't tell which, so neither is claimed. */
    data object MemberLeft : ActivityEvent

    data class ChapterStatusChanged(
        override val chapterId: String,
        override val chapterTitle: String,
        val status: ChapterStatus,
    ) : ChapterEvent

    data class DraftUploaded(
        override val chapterId: String,
        override val chapterTitle: String,
        val versionNumber: Int,
    ) : ChapterEvent

    data class FeedbackPosted(
        override val chapterId: String,
        override val chapterTitle: String,
        val versionNumber: Int?,
    ) : ChapterEvent

    data class FeedbackResolved(
        override val chapterId: String,
        override val chapterTitle: String,
    ) : ChapterEvent

    data class FeedbackReopened(
        override val chapterId: String,
        override val chapterTitle: String,
    ) : ChapterEvent

    data class TaskCreated(
        override val taskId: String,
        override val taskTitle: String,
    ) : TaskEvent

    data class TaskStatusChanged(
        override val taskId: String,
        override val taskTitle: String,
        val status: TaskStatus,
    ) : TaskEvent
}
