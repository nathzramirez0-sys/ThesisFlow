package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.FileKind
import java.time.Instant

@Entity(tableName = "chapters", indices = [Index("groupId")])
data class ChapterEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    /** "order" is an SQL keyword, hence the different column name. */
    val sortOrder: Int,
    val title: String,
    val status: ChapterStatus,
    val deadline: Instant?,
    val latestVersion: Int,
    val updatedAt: Instant?,
    val hasPendingWrites: Boolean,
)

/** One uploaded draft of a chapter. The number is the key, so v3 can only exist once. */
@Entity(
    tableName = "chapter_versions",
    primaryKeys = ["chapterId", "versionNumber"],
    indices = [Index("groupId")],
)
data class ChapterVersionEntity(
    val groupId: String,
    val chapterId: String,
    val versionNumber: Int,
    val fileId: String,
    val note: String,
    val uploadedBy: String,
    val uploadedAt: Instant?,
)

/** Every file in a group: drafts, task attachments and feedback files. */
@Entity(tableName = "files", indices = [Index("groupId"), Index("chapterId")])
data class FileEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val storagePath: String,
    val kind: FileKind,
    val chapterId: String?,
    val taskId: String?,
    val feedbackId: String?,
    val uploadedBy: String,
    val uploadedAt: Instant?,
)

/**
 * A version with its file and uploader, loaded in one query. The uploader list can
 * hold the same person's entry from other groups; the mapper picks this group's.
 */
data class VersionWithDetails(
    @Embedded val version: ChapterVersionEntity,
    @Relation(parentColumn = "fileId", entityColumn = "id")
    val file: FileEntity?,
    @Relation(parentColumn = "uploadedBy", entityColumn = "uid")
    val uploaders: List<MemberEntity>,
)
