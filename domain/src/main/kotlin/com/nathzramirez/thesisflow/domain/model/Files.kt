package com.nathzramirez.thesisflow.domain.model

import com.nathzramirez.thesisflow.domain.result.DomainError
import java.time.Instant

enum class FileKind {
    /** A chapter draft; each one is a numbered version. Uploaded by students. */
    DRAFT,
    /** A file on a task. Uploaded by students. */
    ATTACHMENT,
    /** A file on adviser feedback, e.g. a marked-up draft. Uploaded by advisers. */
    FEEDBACK,
}

data class FileAttachment(
    val id: String,
    val groupId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: FileKind,
    val chapterId: String?,
    val uploadedBy: String,
    val uploadedAt: Instant?,
)

data class ChapterVersion(
    val chapterId: String,
    val versionNumber: Int,
    val note: String,
    val uploadedBy: String,
    /** Null when the uploader has since left the group. */
    val uploaderName: String?,
    val uploadedAt: Instant?,
    /** Null for a moment after the version syncs but before its file record does. */
    val file: FileAttachment?,
)

/** What the picker handed us, read before anything is copied or uploaded. */
data class LocalFileInfo(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
)

enum class UploadState { QUEUED, UPLOADING, FAILED }

/** An upload that exists only on this phone until it reaches the server. */
data class PendingUpload(
    val fileId: String,
    val groupId: String,
    val chapterId: String?,
    /** Set for files on adviser feedback, so each shows under its feedback. */
    val feedbackId: String?,
    val fileName: String,
    val sizeBytes: Long,
    val state: UploadState,
    val progressPercent: Int,
    val error: DomainError?,
)
