package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nathzramirez.thesisflow.domain.model.FileKind
import com.nathzramirez.thesisflow.domain.model.UploadState
import java.time.Instant

/**
 * An upload waiting to reach the server. Unlike every other table, this data
 * exists nowhere else yet, which is why the database now uses real migrations
 * instead of dropping and re-syncing on a schema change.
 *
 * [fileId] is chosen before the upload starts, so a retry writes to the same
 * Storage path and Firestore document instead of creating a duplicate.
 */
@Entity(tableName = "pending_uploads", indices = [Index("chapterId"), Index("taskId")])
data class PendingUploadEntity(
    @PrimaryKey val fileId: String,
    val groupId: String,
    val chapterId: String?,
    val taskId: String?,
    val kind: FileKind,
    /** Copy in app storage, so the upload doesn't depend on the picker's temporary permission. */
    val cachedPath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val note: String,
    val uploadedBy: String,
    val state: UploadState,
    val progressPercent: Int,
    /** Why the last attempt failed, as an [UploadFailure] name. */
    val failure: String?,
    val createdAt: Instant,
)

/** Reasons an upload stopped for good; stored by name in [PendingUploadEntity.failure]. */
enum class UploadFailure { PERMISSION_DENIED, TARGET_DELETED, FILE_MISSING, UNKNOWN }
