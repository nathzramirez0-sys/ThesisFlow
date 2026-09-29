package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nathzramirez.thesisflow.domain.model.Role
import java.time.Instant

@Entity(tableName = "feedback", indices = [Index("chapterId"), Index("groupId")])
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val chapterId: String,
    val body: String,
    val authorId: String,
    val authorName: String,
    val authorRole: Role,
    val versionNumber: Int?,
    val resolved: Boolean,
    val resolvedBy: String?,
    val resolvedAt: Instant?,
    val createdAt: Instant?,
    val hasPendingWrites: Boolean,
)

/**
 * Feedback with its files and the member who resolved it, loaded in one query.
 * Like [VersionWithDetails], the resolver list can hold the same person's entry
 * from other groups; the mapper picks this group's.
 */
data class FeedbackWithDetails(
    @Embedded val feedback: FeedbackEntity,
    @Relation(parentColumn = "id", entityColumn = "feedbackId")
    val files: List<FileEntity>,
    @Relation(parentColumn = "resolvedBy", entityColumn = "uid")
    val resolvers: List<MemberEntity>,
)

/** One row of the open-feedback count per chapter. */
data class OpenFeedbackCount(val chapterId: String, val openCount: Int)
