package com.nathzramirez.thesisflow.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nathzramirez.thesisflow.data.local.dao.FeedbackDao
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.NotSignedInException
import com.nathzramirez.thesisflow.data.remote.awaitOrQueued
import com.nathzramirez.thesisflow.data.remote.requireUid
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Feedback as FeedbackFields

/**
 * Writes go to Firestore only; the sync manager's listener brings each change
 * back into Room within milliseconds, even offline.
 */
@Singleton
internal class FeedbackRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val feedbackDao: FeedbackDao,
    private val groupDao: GroupDao,
    private val userDao: UserDao,
) : FeedbackRepository {

    private fun chapter(groupId: String, chapterId: String) =
        firestore.collection(Groups.COLLECTION).document(groupId)
            .collection(Chapters.COLLECTION).document(chapterId)

    private fun feedback(groupId: String, chapterId: String, feedbackId: String) =
        chapter(groupId, chapterId).collection(FeedbackFields.COLLECTION).document(feedbackId)

    override fun observeFeedback(chapterId: String): Flow<List<Feedback>> =
        feedbackDao.observeForChapter(chapterId).map { items -> items.map { it.toDomain() } }

    override fun observeFeedbackById(feedbackId: String): Flow<Feedback?> =
        feedbackDao.observe(feedbackId).map { it?.toDomain() }

    override fun observeOpenCounts(groupId: String): Flow<Map<String, Int>> =
        feedbackDao.observeOpenCounts(groupId).map { rows -> rows.associate { it.chapterId to it.openCount } }

    /**
     * One batch, so the feedback and the move to Revisions reach the server
     * together or not at all. The author's name and role are copied in; the
     * rules check the role against the group's roles map.
     */
    override suspend fun postFeedback(
        groupId: String,
        chapterId: String,
        body: String,
        versionNumber: Int?,
        moveToRevisions: Boolean,
    ): AppResult<String> = safeCall {
        val uid = auth.requireUid()
        val role = groupDao.observe(groupId).first()?.myRole ?: throw NotSignedInException()
        val doc = chapter(groupId, chapterId).collection(FeedbackFields.COLLECTION).document()
        val batch = firestore.batch()
        batch.set(
            doc,
            mapOf(
                FeedbackFields.GROUP_ID to groupId,
                FeedbackFields.CHAPTER_ID to chapterId,
                FeedbackFields.BODY to body,
                FeedbackFields.AUTHOR_ID to uid,
                FeedbackFields.AUTHOR_NAME to userDao.get(uid)?.displayName.orEmpty(),
                FeedbackFields.AUTHOR_ROLE to role.toWire(),
                FeedbackFields.VERSION_NUMBER to versionNumber,
                FeedbackFields.RESOLVED to false,
                FeedbackFields.RESOLVED_BY to null,
                FeedbackFields.RESOLVED_AT to null,
                FeedbackFields.CREATED_AT to FieldValue.serverTimestamp(),
                FeedbackFields.UPDATED_AT to FieldValue.serverTimestamp(),
                FeedbackFields.UPDATED_BY to uid,
            ),
        )
        if (moveToRevisions) {
            batch.update(
                chapter(groupId, chapterId),
                mapOf(
                    Chapters.STATUS to ChapterStatus.REVISIONS.toWire(),
                    Chapters.UPDATED_AT to FieldValue.serverTimestamp(),
                    Chapters.UPDATED_BY to uid,
                ),
            )
        }
        batch.commit().awaitOrQueued()
        doc.id
    }

    override suspend fun setResolved(
        groupId: String,
        chapterId: String,
        feedbackId: String,
        resolved: Boolean,
    ): AppResult<Unit> = safeCall {
        val uid = auth.requireUid()
        feedback(groupId, chapterId, feedbackId).update(
            mapOf(
                FeedbackFields.RESOLVED to resolved,
                FeedbackFields.RESOLVED_BY to if (resolved) uid else null,
                FeedbackFields.RESOLVED_AT to if (resolved) FieldValue.serverTimestamp() else null,
                FeedbackFields.UPDATED_AT to FieldValue.serverTimestamp(),
                FeedbackFields.UPDATED_BY to uid,
            ),
        ).awaitOrQueued()
    }

    override suspend fun deleteFeedback(groupId: String, chapterId: String, feedbackId: String): AppResult<Unit> =
        safeCall { feedback(groupId, chapterId, feedbackId).delete().awaitOrQueued() }
}
