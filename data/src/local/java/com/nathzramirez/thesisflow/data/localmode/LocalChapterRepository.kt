package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.model.DefaultChapters
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

internal class LocalChapterRepository @Inject constructor(
    private val session: LocalSession,
    private val chapterDao: ChapterDao,
    private val versionDao: ChapterVersionDao,
    private val localModeDao: LocalModeDao,
    private val activity: LocalActivityLog,
    private val cleanup: LocalCleanup,
) : ChapterRepository {

    override fun observeChapters(groupId: String): Flow<List<Chapter>> =
        chapterDao.observeForGroup(groupId).map { chapters -> chapters.map { it.toDomain() } }

    /**
     * Only the current person's groups. The table holds everyone's chapters here,
     * and the daily reminders read this list, so others' deadlines must stay out.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllChapters(): Flow<List<Chapter>> = session.currentUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            combine(localModeDao.observeGroupsOf(uid), chapterDao.observeAll()) { groups, chapters ->
                val mine = groups.mapTo(HashSet()) { it.id }
                chapters.filter { it.groupId in mine }.map { it.toDomain() }
            }
        }
    }

    override fun observeChapter(chapterId: String): Flow<Chapter?> =
        chapterDao.observe(chapterId).map { it?.toDomain() }

    override fun observeVersions(chapterId: String): Flow<List<ChapterVersion>> =
        versionDao.observeForChapter(chapterId).map { versions -> versions.map { it.toDomain() } }

    override fun observeGroupVersions(groupId: String): Flow<List<ChapterVersion>> =
        versionDao.observeForGroup(groupId).map { versions -> versions.map { it.toDomain() } }

    override suspend fun addChapter(groupId: String, title: String): AppResult<String> = localCall {
        session.requireUid()
        val order = (chapterDao.maxOrder(groupId) ?: 0) + 1
        val chapter = newChapterEntity(groupId, title, order, Instant.now())
        chapterDao.upsertAll(listOf(chapter))
        chapter.id
    }

    override suspend fun addDefaultChapters(groupId: String): AppResult<Unit> = localCall {
        session.requireUid()
        val now = Instant.now()
        chapterDao.upsertAll(
            DefaultChapters.titles.mapIndexed { index, title -> newChapterEntity(groupId, title, index + 1, now) },
        )
    }

    override suspend fun renameChapter(groupId: String, chapterId: String, title: String): AppResult<Unit> =
        update(chapterId) { it.copy(title = title) }

    override suspend fun deleteChapter(groupId: String, chapterId: String): AppResult<Unit> = localCall {
        session.requireUid()
        cleanup.deleteChapter(chapterId)
    }

    /** A real change of status goes in the feed, as the chapterActivity function records it. */
    override suspend fun setStatus(groupId: String, chapterId: String, status: ChapterStatus): AppResult<Unit> =
        localCall {
            val uid = session.requireUid()
            val chapter = localModeDao.chapter(chapterId) ?: throw LocalFailure(DomainError.NotFound)
            if (chapter.status == status) return@localCall
            chapterDao.upsertAll(listOf(chapter.copy(status = status, updatedAt = Instant.now())))
            activity.record(
                groupId,
                ActivityTypes.CHAPTER_STATUS,
                actorId = uid,
                targetId = chapterId,
                targetTitle = chapter.title,
                detail = status.toWire(),
            )
        }

    override suspend fun setDeadline(groupId: String, chapterId: String, deadline: Instant?): AppResult<Unit> =
        update(chapterId) { it.copy(deadline = deadline) }

    private suspend fun update(chapterId: String, change: (ChapterEntity) -> ChapterEntity): AppResult<Unit> =
        localCall {
            session.requireUid()
            val chapter = localModeDao.chapter(chapterId) ?: throw LocalFailure(DomainError.NotFound)
            chapterDao.upsertAll(listOf(change(chapter).copy(updatedAt = Instant.now())))
        }
}
