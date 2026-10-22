package com.nathzramirez.thesisflow.data.localmode

import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.FeedbackDao
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.entity.FeedbackEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

internal class LocalFeedbackRepository @Inject constructor(
    private val session: LocalSession,
    private val database: ThesisFlowDatabase,
    private val feedbackDao: FeedbackDao,
    private val chapterDao: ChapterDao,
    private val localModeDao: LocalModeDao,
    private val activity: LocalActivityLog,
    private val cleanup: LocalCleanup,
) : FeedbackRepository {

    override fun observeFeedback(chapterId: String): Flow<List<Feedback>> =
        feedbackDao.observeForChapter(chapterId).map { items -> items.map { it.toDomain() } }

    override fun observeFeedbackById(feedbackId: String): Flow<Feedback?> =
        feedbackDao.observe(feedbackId).map { it?.toDomain() }

    override fun observeGroupFeedback(groupId: String): Flow<List<Feedback>> =
        feedbackDao.observeForGroup(groupId).map { items -> items.map { it.toDomain() } }

    override fun observeOpenCounts(groupId: String): Flow<Map<String, Int>> =
        feedbackDao.observeOpenCounts(groupId).map { rows -> rows.associate { it.chapterId to it.openCount } }

    /** The feedback and the move to Revisions are one transaction, so nobody sees one without the other. */
    override suspend fun postFeedback(
        groupId: String,
        chapterId: String,
        body: String,
        versionNumber: Int?,
        moveToRevisions: Boolean,
    ): AppResult<String> = localCall {
        val uid = session.requireUid()
        val author = localModeDao.member(groupId, uid) ?: throw LocalFailure(DomainError.PermissionDenied)
        val chapter = localModeDao.chapter(chapterId) ?: throw LocalFailure(DomainError.NotFound)
        val feedbackId = newId()
        val now = Instant.now()
        database.withTransaction {
            feedbackDao.upsertAll(
                listOf(
                    FeedbackEntity(
                        id = feedbackId,
                        groupId = groupId,
                        chapterId = chapterId,
                        body = body,
                        authorId = uid,
                        authorName = author.displayName,
                        authorRole = author.role,
                        versionNumber = versionNumber,
                        resolved = false,
                        resolvedBy = null,
                        resolvedAt = null,
                        createdAt = now,
                        hasPendingWrites = false,
                    ),
                ),
            )
            activity.record(
                groupId,
                ActivityTypes.FEEDBACK_POSTED,
                actorId = uid,
                targetId = chapterId,
                targetTitle = chapter.title,
                detail = versionNumber?.toString(),
            )
            if (moveToRevisions && chapter.status != ChapterStatus.REVISIONS) {
                chapterDao.upsertAll(listOf(chapter.copy(status = ChapterStatus.REVISIONS, updatedAt = now)))
                activity.record(
                    groupId,
                    ActivityTypes.CHAPTER_STATUS,
                    actorId = uid,
                    targetId = chapterId,
                    targetTitle = chapter.title,
                    detail = ChapterStatus.REVISIONS.toWire(),
                )
            }
        }
        feedbackId
    }

    override suspend fun setResolved(
        groupId: String,
        chapterId: String,
        feedbackId: String,
        resolved: Boolean,
    ): AppResult<Unit> = localCall {
        val uid = session.requireUid()
        val item = localModeDao.feedback(feedbackId) ?: throw LocalFailure(DomainError.NotFound)
        if (item.resolved == resolved) return@localCall
        database.withTransaction {
            feedbackDao.upsertAll(
                listOf(
                    item.copy(
                        resolved = resolved,
                        resolvedBy = if (resolved) uid else null,
                        resolvedAt = if (resolved) Instant.now() else null,
                    ),
                ),
            )
            activity.record(
                groupId,
                if (resolved) ActivityTypes.FEEDBACK_RESOLVED else ActivityTypes.FEEDBACK_REOPENED,
                actorId = uid,
                targetId = chapterId,
                targetTitle = localModeDao.chapter(chapterId)?.title.orEmpty(),
            )
        }
    }

    override suspend fun deleteFeedback(groupId: String, chapterId: String, feedbackId: String): AppResult<Unit> =
        localCall {
            session.requireUid()
            cleanup.deleteFeedback(feedbackId)
        }
}
