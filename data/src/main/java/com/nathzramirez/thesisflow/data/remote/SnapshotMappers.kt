package com.nathzramirez.thesisflow.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.nathzramirez.thesisflow.data.local.entity.ActivityEntity
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.ChapterVersionEntity
import com.nathzramirez.thesisflow.data.local.entity.FeedbackEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskAssigneeEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskCommentEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskRow
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Activity
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Comments
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Feedback
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Files
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Invites
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Members
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Tasks
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Users
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Versions
import com.nathzramirez.thesisflow.domain.model.Invite
import java.time.Instant

/*
 * Firestore documents are mapped by hand instead of with toObject(). There is no
 * reflection, missing fields get explicit defaults, and a malformed document is
 * skipped (null) instead of crashing the sync.
 */

internal fun DocumentSnapshot.toUserEntity(): UserEntity? {
    if (!exists()) return null
    return UserEntity(
        uid = id,
        email = getString(Users.EMAIL).orEmpty(),
        displayName = getString(Users.DISPLAY_NAME).orEmpty(),
        photoUrl = getString(Users.PHOTO_URL),
        course = getString(Users.COURSE).orEmpty(),
        school = getString(Users.SCHOOL).orEmpty(),
        onboardingComplete = getBoolean(Users.ONBOARDING_COMPLETE) ?: false,
    )
}

/** Returns null when [currentUid] has no role in the group, e.g. while a removal is syncing. */
internal fun DocumentSnapshot.toGroupEntity(currentUid: String): GroupEntity? {
    if (!exists()) return null
    val roles = get(Groups.ROLES) as? Map<*, *> ?: return null
    val myRole = roleFromWire(roles[currentUid] as? String) ?: return null
    val memberIds = get(Groups.MEMBER_IDS) as? List<*> ?: emptyList<Any>()
    return GroupEntity(
        id = id,
        name = getString(Groups.NAME).orEmpty(),
        thesisTitle = getString(Groups.THESIS_TITLE).orEmpty(),
        course = getString(Groups.COURSE).orEmpty(),
        school = getString(Groups.SCHOOL).orEmpty(),
        myRole = myRole,
        memberCount = memberIds.size,
        proposalDefenseAt = instant(Groups.PROPOSAL_DEFENSE_AT),
        finalDefenseAt = instant(Groups.FINAL_DEFENSE_AT),
        createdAt = instant(Groups.CREATED_AT),
        updatedAt = instant(Groups.UPDATED_AT),
    )
}

internal fun DocumentSnapshot.toMemberEntity(groupId: String): MemberEntity? {
    if (!exists()) return null
    val role = roleFromWire(getString(Members.ROLE)) ?: return null
    return MemberEntity(
        groupId = groupId,
        uid = id,
        displayName = getString(Members.DISPLAY_NAME).orEmpty(),
        photoUrl = getString(Members.PHOTO_URL),
        role = role,
        joinedAt = instant(Members.JOINED_AT),
    )
}

internal fun DocumentSnapshot.toInvite(): Invite? {
    if (!exists()) return null
    val code = getString(Invites.CODE) ?: return null
    val role = roleFromWire(getString(Invites.ROLE)) ?: return null
    val expiresAt = instant(Invites.EXPIRES_AT) ?: return null
    return Invite(code = code, role = role, expiresAt = expiresAt)
}

internal fun DocumentSnapshot.toChapterEntity(groupId: String): ChapterEntity? {
    if (!exists()) return null
    val status = chapterStatusFromWire(getString(Chapters.STATUS)) ?: return null
    return ChapterEntity(
        id = id,
        groupId = groupId,
        sortOrder = getLong(Chapters.ORDER)?.toInt() ?: 0,
        title = getString(Chapters.TITLE).orEmpty(),
        status = status,
        deadline = instant(Chapters.DEADLINE),
        latestVersion = getLong(Chapters.LATEST_VERSION)?.toInt() ?: 0,
        updatedAt = instant(Chapters.UPDATED_AT),
        hasPendingWrites = metadata.hasPendingWrites(),
    )
}

internal fun DocumentSnapshot.toVersionEntity(): ChapterVersionEntity? {
    if (!exists()) return null
    return ChapterVersionEntity(
        groupId = getString(Versions.GROUP_ID) ?: return null,
        chapterId = getString(Versions.CHAPTER_ID) ?: return null,
        versionNumber = getLong(Versions.VERSION_NUMBER)?.toInt() ?: return null,
        fileId = getString(Versions.FILE_ID) ?: return null,
        note = getString(Versions.NOTE).orEmpty(),
        uploadedBy = getString(Versions.UPLOADED_BY).orEmpty(),
        uploadedAt = instant(Versions.UPLOADED_AT),
    )
}

internal fun DocumentSnapshot.toFileEntity(groupId: String): FileEntity? {
    if (!exists()) return null
    return FileEntity(
        id = id,
        groupId = groupId,
        name = getString(Files.NAME) ?: return null,
        mimeType = getString(Files.MIME_TYPE).orEmpty(),
        sizeBytes = getLong(Files.SIZE_BYTES) ?: 0,
        storagePath = getString(Files.STORAGE_PATH) ?: return null,
        kind = fileKindFromWire(getString(Files.KIND)) ?: return null,
        chapterId = getString(Files.CHAPTER_ID),
        taskId = getString(Files.TASK_ID),
        feedbackId = getString(Files.FEEDBACK_ID),
        uploadedBy = getString(Files.UPLOADED_BY).orEmpty(),
        uploadedAt = instant(Files.UPLOADED_AT),
    )
}

internal fun DocumentSnapshot.toTaskRow(groupId: String): TaskRow? {
    if (!exists()) return null
    val task = TaskEntity(
        id = id,
        groupId = groupId,
        title = getString(Tasks.TITLE) ?: return null,
        description = getString(Tasks.DESCRIPTION).orEmpty(),
        status = taskStatusFromWire(getString(Tasks.STATUS)) ?: return null,
        priority = taskPriorityFromWire(getString(Tasks.PRIORITY)) ?: return null,
        dueAt = instant(Tasks.DUE_AT),
        chapterId = getString(Tasks.CHAPTER_ID),
        createdBy = getString(Tasks.CREATED_BY).orEmpty(),
        createdAt = instant(Tasks.CREATED_AT),
        completedAt = instant(Tasks.COMPLETED_AT),
        completedBy = getString(Tasks.COMPLETED_BY),
        hasPendingWrites = metadata.hasPendingWrites(),
    )
    val assignees = (get(Tasks.ASSIGNEE_IDS) as? List<*>).orEmpty()
        .filterIsInstance<String>()
        .distinct()
        .map { uid -> TaskAssigneeEntity(taskId = id, uid = uid, groupId = groupId) }
    return TaskRow(task, assignees)
}

internal fun DocumentSnapshot.toCommentEntity(): TaskCommentEntity? {
    if (!exists()) return null
    return TaskCommentEntity(
        id = id,
        groupId = getString(Comments.GROUP_ID) ?: return null,
        taskId = getString(Comments.TASK_ID) ?: return null,
        body = getString(Comments.BODY).orEmpty(),
        authorId = getString(Comments.AUTHOR_ID).orEmpty(),
        authorName = getString(Comments.AUTHOR_NAME).orEmpty(),
        createdAt = instant(Comments.CREATED_AT),
        hasPendingWrites = metadata.hasPendingWrites(),
    )
}

internal fun DocumentSnapshot.toFeedbackEntity(): FeedbackEntity? {
    if (!exists()) return null
    return FeedbackEntity(
        id = id,
        groupId = getString(Feedback.GROUP_ID) ?: return null,
        chapterId = getString(Feedback.CHAPTER_ID) ?: return null,
        body = getString(Feedback.BODY).orEmpty(),
        authorId = getString(Feedback.AUTHOR_ID).orEmpty(),
        authorName = getString(Feedback.AUTHOR_NAME).orEmpty(),
        authorRole = roleFromWire(getString(Feedback.AUTHOR_ROLE)) ?: return null,
        versionNumber = getLong(Feedback.VERSION_NUMBER)?.toInt(),
        resolved = getBoolean(Feedback.RESOLVED) ?: false,
        resolvedBy = getString(Feedback.RESOLVED_BY),
        resolvedAt = instant(Feedback.RESOLVED_AT),
        createdAt = instant(Feedback.CREATED_AT),
        hasPendingWrites = metadata.hasPendingWrites(),
    )
}

/** The type stays a string here; unknown types are filtered out when read, not when synced. */
internal fun DocumentSnapshot.toActivityEntity(groupId: String): ActivityEntity? {
    if (!exists()) return null
    return ActivityEntity(
        id = id,
        groupId = groupId,
        type = getString(Activity.TYPE) ?: return null,
        actorId = getString(Activity.ACTOR_ID).orEmpty(),
        actorName = getString(Activity.ACTOR_NAME).orEmpty(),
        targetId = getString(Activity.TARGET_ID),
        targetTitle = getString(Activity.TARGET_TITLE).orEmpty(),
        detail = getString(Activity.DETAIL),
        createdAt = instant(Activity.CREATED_AT),
    )
}

/**
 * Reads a timestamp. A server timestamp that hasn't been confirmed yet (an offline
 * write) is estimated from the device clock instead of returned as null.
 */
private fun DocumentSnapshot.instant(field: String): Instant? =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant()
