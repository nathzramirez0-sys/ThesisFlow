package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

/**
 * A push from the server, already decoded. The app chooses the words, so they
 * follow the phone's language like the rest of the UI.
 */
data class PushNotice(
    /** Whom the server meant it for; a phone signed in as someone else drops it. */
    val recipientId: String,
    val groupId: String,
    val groupName: String,
    val actorName: String,
    val event: PushEvent,
)

/** The pushes worth interrupting someone for. Everything else stays in the feed. */
sealed interface PushEvent {
    data class MemberJoined(val role: Role) : PushEvent

    /** A student sent a chapter to the adviser. */
    data class ReadyForReview(val chapterId: String, val chapterTitle: String) : PushEvent
    data class ChapterApproved(val chapterId: String, val chapterTitle: String) : PushEvent
    data class RevisionsRequested(val chapterId: String, val chapterTitle: String) : PushEvent

    data class FeedbackPosted(
        val chapterId: String,
        val chapterTitle: String,
        val versionNumber: Int?,
        /** The first lines of the comment. */
        val preview: String,
    ) : PushEvent
    data class FeedbackReopened(val chapterId: String, val chapterTitle: String) : PushEvent
    data class FeedbackResolved(val chapterId: String, val chapterTitle: String) : PushEvent

    data class TaskAssigned(val taskId: String, val taskTitle: String) : PushEvent
    data class TaskDone(val taskId: String, val taskTitle: String) : PushEvent

    data class DefenseScheduled(val kind: DefenseKind, val at: Instant) : PushEvent
}
