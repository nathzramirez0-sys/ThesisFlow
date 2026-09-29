package com.nathzramirez.thesisflow.data.local

import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.PendingUploadEntity
import com.nathzramirez.thesisflow.data.local.entity.UploadFailure
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.local.entity.VersionWithDetails
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.PendingUpload
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
    fileName = fileName,
    sizeBytes = sizeBytes,
    state = state,
    progressPercent = progressPercent,
    error = failure?.let { name ->
        when (runCatching { UploadFailure.valueOf(name) }.getOrDefault(UploadFailure.UNKNOWN)) {
            UploadFailure.PERMISSION_DENIED -> DomainError.PermissionDenied
            UploadFailure.CHAPTER_DELETED -> DomainError.NotFound
            UploadFailure.FILE_MISSING -> DomainError.FileUnreadable
            UploadFailure.UNKNOWN -> DomainError.Unknown(null)
        }
    },
)
