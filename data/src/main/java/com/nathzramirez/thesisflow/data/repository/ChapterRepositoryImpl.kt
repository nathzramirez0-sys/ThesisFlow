package com.nathzramirez.thesisflow.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.awaitOrQueued
import com.nathzramirez.thesisflow.data.remote.newChapterFields
import com.nathzramirez.thesisflow.data.remote.requireUid
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.model.DefaultChapters
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes go to Firestore only; the sync manager's listener brings each change
 * back into Room within milliseconds, even offline, so there is one writer to Room.
 */
@Singleton
internal class ChapterRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val chapterDao: ChapterDao,
    private val versionDao: ChapterVersionDao,
) : ChapterRepository {

    private fun chapters(groupId: String) =
        firestore.collection(Groups.COLLECTION).document(groupId).collection(Chapters.COLLECTION)

    override fun observeChapters(groupId: String): Flow<List<Chapter>> =
        chapterDao.observeForGroup(groupId).map { chapters -> chapters.map { it.toDomain() } }

    override fun observeAllChapters(): Flow<List<Chapter>> =
        chapterDao.observeAll().map { chapters -> chapters.map { it.toDomain() } }

    override fun observeChapter(chapterId: String): Flow<Chapter?> =
        chapterDao.observe(chapterId).map { it?.toDomain() }

    override fun observeVersions(chapterId: String): Flow<List<ChapterVersion>> =
        versionDao.observeForChapter(chapterId).map { versions -> versions.map { it.toDomain() } }

    override fun observeGroupVersions(groupId: String): Flow<List<ChapterVersion>> =
        versionDao.observeForGroup(groupId).map { versions -> versions.map { it.toDomain() } }

    override suspend fun addChapter(groupId: String, title: String): AppResult<String> = safeCall {
        val uid = auth.requireUid()
        val order = (chapterDao.maxOrder(groupId) ?: 0) + 1
        val chapter = chapters(groupId).document()
        chapter.set(newChapterFields(title, order, uid)).awaitOrQueued()
        chapter.id
    }

    override suspend fun addDefaultChapters(groupId: String): AppResult<Unit> = safeCall {
        val uid = auth.requireUid()
        val batch = firestore.batch()
        DefaultChapters.titles.forEachIndexed { index, title ->
            batch.set(chapters(groupId).document(), newChapterFields(title, index + 1, uid))
        }
        batch.commit().awaitOrQueued()
    }

    override suspend fun renameChapter(groupId: String, chapterId: String, title: String) =
        update(groupId, chapterId, Chapters.TITLE to title)

    override suspend fun deleteChapter(groupId: String, chapterId: String): AppResult<Unit> = safeCall {
        chapters(groupId).document(chapterId).delete().awaitOrQueued()
    }

    override suspend fun setStatus(groupId: String, chapterId: String, status: ChapterStatus) =
        update(groupId, chapterId, Chapters.STATUS to status.toWire())

    override suspend fun setDeadline(groupId: String, chapterId: String, deadline: Instant?) =
        update(groupId, chapterId, Chapters.DEADLINE to deadline?.let { Timestamp(it) })

    /** Every edit also records who made it and when; the rules require both. */
    private suspend fun update(groupId: String, chapterId: String, change: Pair<String, Any?>): AppResult<Unit> =
        safeCall {
            val uid = auth.requireUid()
            chapters(groupId).document(chapterId).update(
                mapOf(
                    change,
                    Chapters.UPDATED_AT to FieldValue.serverTimestamp(),
                    Chapters.UPDATED_BY to uid,
                ),
            ).awaitOrQueued()
        }
}
