package com.nathzramirez.thesisflow.data.local

import com.nathzramirez.thesisflow.data.local.entity.ActivityEntity
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.FeedbackWithDetails
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.PendingUploadEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskCommentEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskWithAssignees
import com.nathzramirez.thesisflow.data.local.entity.UploadFailure
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.local.entity.VersionWithDetails
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.chapterStatusFromWire
import com.nathzramirez.thesisflow.data.remote.roleFromWire
import com.nathzramirez.thesisflow.data.remote.taskStatusFromWire
import com.nathzramirez.thesisflow.domain.model.Activity
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.User
import com.nathzramirez.thesisflow.domain.result.DomainError

internal fun UserEntity.toDomain() = User(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    course = course,
    school = school,
    onboardingComplete = onboardingComplete,
)

internal fun GroupEntity.toDomain() = Group(
    id = id,
    name = name,
    thesisTitle = thesisTitle,
    course = course,
    school = school,
    myRole = myRole,
    memberCount = memberCount,
    proposalDefenseAt = proposalDefenseAt,
    finalDefenseAt = finalDefenseAt,
    createdAt = createdAt,
)

internal fun MemberEntity.toDomain() = Member(
    uid = uid,
    displayName = displayName,
    photoUrl = photoUrl,
    role = role,
    joinedAt = joinedAt,
)

internal fun ChapterEntity.toDomain() = Chapter(
    id = id,
    groupId = groupId,
    order = sortOrder,
    title = title,
    status = status,
    deadline = deadline,
    latestVersion = latestVersion,
    updatedAt = updatedAt,
    hasPendingWrites = hasPendingWrites,
)

internal fun FileEntity.toDomain() = FileAttachment(
    id = id,
    groupId = groupId,
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    kind = kind,
    chapterId = chapterId,
    uploadedBy = uploadedBy,
    uploadedAt = uploadedAt,
)

internal fun VersionWithDetails.toDomain() = ChapterVersion(
    chapterId = version.chapterId,
    versionNumber = version.versionNumber,
    note = version.note,
    uploadedBy = version.uploadedBy,
    uploaderName = uploaders.firstOrNull { it.groupId == version.groupId }?.displayName,
    uploadedAt = version.uploadedAt,
    file = file?.toDomain(),
)

internal fun PendingUploadEntity.toDomain() = PendingUpload(
    fileId = fileId,
    groupId = groupId,
    chapterId = chapterId,
    feedbackId = feedbackId,
    fileName = fileName,
    sizeBytes = sizeBytes,
    state = state,
    progressPercent = progressPercent,
    error = failure?.let { name ->
        when (runCatching { UploadFailure.valueOf(name) }.getOrDefault(UploadFailure.UNKNOWN)) {
            UploadFailure.PERMISSION_DENIED -> DomainError.PermissionDenied
            UploadFailure.TARGET_DELETED -> DomainError.NotFound
            UploadFailure.FILE_MISSING -> DomainError.FileUnreadable
            UploadFailure.UNKNOWN -> DomainError.Unknown(null)
        }
    },
)

internal fun TaskWithAssignees.toDomain() = Task(
    id = task.id,
    groupId = task.groupId,
    title = task.title,
    description = task.description,
    status = task.status,
    priority = task.priority,
    dueAt = task.dueAt,
    chapterId = task.chapterId,
    assigneeIds = assignees.map { it.uid }.toSet(),
    createdBy = task.createdBy,
    createdAt = task.createdAt,
    completedAt = task.completedAt,
    hasPendingWrites = task.hasPendingWrites,
)

internal fun TaskCommentEntity.toDomain() = TaskComment(
    id = id,
    taskId = taskId,
    body = body,
    authorId = authorId,
    authorName = authorName,
    createdAt = createdAt,
    hasPendingWrites = hasPendingWrites,
)

internal fun FeedbackWithDetails.toDomain() = Feedback(
    id = feedback.id,
    groupId = feedback.groupId,
    chapterId = feedback.chapterId,
    body = feedback.body,
    authorId = feedback.authorId,
    authorName = feedback.authorName,
    authorRole = feedback.authorRole,
    versionNumber = feedback.versionNumber,
    resolved = feedback.resolved,
    resolvedBy = feedback.resolvedBy,
    resolverName = resolvers.firstOrNull { it.groupId == feedback.groupId }?.displayName,
    resolvedAt = feedback.resolvedAt,
    createdAt = feedback.createdAt,
    hasPendingWrites = feedback.hasPendingWrites,
    files = files.sortedBy { it.uploadedAt }.map { it.toDomain() },
)

/** Null for entry types this version doesn't know, or entries missing what their type needs. */
internal fun ActivityEntity.toDomain(): Activity? {
    val event = toEvent() ?: return null
    return Activity(
        id = id,
        groupId = groupId,
        actorId = actorId,
        actorName = actorName,
        event = event,
        createdAt = createdAt,
    )
}

private fun ActivityEntity.toEvent(): ActivityEvent? = when (type) {
    ActivityTypes.MEMBER_JOINED -> roleFromWire(detail)?.let { ActivityEvent.MemberJoined(it) }
    ActivityTypes.MEMBER_LEFT -> ActivityEvent.MemberLeft
    ActivityTypes.CHAPTER_STATUS -> targetId?.let { id ->
        chapterStatusFromWire(detail)?.let { ActivityEvent.ChapterStatusChanged(id, targetTitle, it) }
    }
    ActivityTypes.DRAFT_UPLOADED -> targetId?.let { id ->
        detail?.toIntOrNull()?.let { ActivityEvent.DraftUploaded(id, targetTitle, it) }
    }
    ActivityTypes.FEEDBACK_POSTED -> targetId?.let { ActivityEvent.FeedbackPosted(it, targetTitle, detail?.toIntOrNull()) }
    ActivityTypes.FEEDBACK_RESOLVED -> targetId?.let { ActivityEvent.FeedbackResolved(it, targetTitle) }
    ActivityTypes.FEEDBACK_REOPENED -> targetId?.let { ActivityEvent.FeedbackReopened(it, targetTitle) }
    ActivityTypes.TASK_CREATED -> targetId?.let { ActivityEvent.TaskCreated(it, targetTitle) }
    ActivityTypes.TASK_STATUS -> targetId?.let { id ->
        taskStatusFromWire(detail)?.let { ActivityEvent.TaskStatusChanged(id, targetTitle, it) }
    }
    else -> null
}
