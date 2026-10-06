package com.nathzramirez.thesisflow.data.push

import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.chapterStatusFromWire
import com.nathzramirez.thesisflow.data.remote.defenseKindFromWire
import com.nathzramirez.thesisflow.data.remote.roleFromWire
import com.nathzramirez.thesisflow.data.remote.taskStatusFromWire
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.PushEvent
import com.nathzramirez.thesisflow.domain.model.PushNotice
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import java.time.Instant

/**
 * Decodes the data map sent by functions/src/notify.ts. The wire format is the
 * activity entry's, so the same string constants apply. Anything malformed or
 * unknown gives null and is dropped: a newer server never crashes an older app.
 */
object PushPayloads {
    /** Push-only type: someone was assigned a task (also sent when a task is created). */
    private const val TASK_ASSIGNED = "task_assigned"

    fun parse(data: Map<String, String>): PushNotice? {
        val recipientId = data["recipientId"]?.takeIf { it.isNotBlank() } ?: return null
        val groupId = data["groupId"]?.takeIf { it.isNotBlank() } ?: return null
        val targetId = data["targetId"].orEmpty()
        val title = data["targetTitle"].orEmpty()
        val detail = data["detail"]

        val event: PushEvent = when (data["type"]) {
            ActivityTypes.MEMBER_JOINED -> roleFromWire(detail)?.let { PushEvent.MemberJoined(it) }
            ActivityTypes.CHAPTER_STATUS -> when (chapterStatusFromWire(detail)) {
                ChapterStatus.FOR_REVIEW -> PushEvent.ReadyForReview(targetId, title)
                ChapterStatus.APPROVED -> PushEvent.ChapterApproved(targetId, title)
                ChapterStatus.REVISIONS -> PushEvent.RevisionsRequested(targetId, title)
                else -> null
            }
            ActivityTypes.FEEDBACK_POSTED ->
                PushEvent.FeedbackPosted(targetId, title, detail?.toIntOrNull(), data["preview"].orEmpty())
            ActivityTypes.FEEDBACK_REOPENED -> PushEvent.FeedbackReopened(targetId, title)
            ActivityTypes.FEEDBACK_RESOLVED -> PushEvent.FeedbackResolved(targetId, title)
            TASK_ASSIGNED -> PushEvent.TaskAssigned(targetId, title)
            ActivityTypes.TASK_STATUS ->
                if (taskStatusFromWire(detail) == TaskStatus.DONE) PushEvent.TaskDone(targetId, title) else null
            ActivityTypes.DEFENSE_SCHEDULED -> {
                val kind = defenseKindFromWire(detail)
                val at = data["at"]?.toLongOrNull()?.let(Instant::ofEpochMilli)
                if (kind != null && at != null) PushEvent.DefenseScheduled(kind, at) else null
            }
            else -> null
        } ?: return null

        // Events about a chapter or task are useless without it: the notification couldn't open anything.
        val needsTarget = event !is PushEvent.MemberJoined && event !is PushEvent.DefenseScheduled
        if (needsTarget && targetId.isBlank()) return null

        return PushNotice(
            recipientId = recipientId,
            groupId = groupId,
            groupName = data["groupName"].orEmpty(),
            actorName = data["actorName"].orEmpty(),
            event = event,
        )
    }
}
