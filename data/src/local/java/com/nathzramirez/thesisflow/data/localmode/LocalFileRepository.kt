package com.nathzramirez.thesisflow.data.localmode

import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.di.IoDispatcher
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.dao.FileDao
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.entity.ChapterVersionEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.FileUnreadableException
import com.nathzramirez.thesisflow.data.upload.PickedFiles
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * Files in the local build never leave the phone, so there is no upload queue:
 * a picked file is copied into app storage and recorded straight away, and the
 * "still on its way" lists are always empty.
 */
internal class LocalFileRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val session: LocalSession,
    private val database: ThesisFlowDatabase,
    private val pickedFiles: PickedFiles,
    private val fileStore: LocalFileStore,
    private val fileDao: FileDao,
    private val chapterDao: ChapterDao,
    private val versionDao: ChapterVersionDao,
    private val localModeDao: LocalModeDao,
    private val activity: LocalActivityLog,
) : FileRepository {

    override suspend fun inspect(sourceUri: String): AppResult<LocalFileInfo> = localCall {
        withContext(ioDispatcher) { pickedFiles.inspect(sourceUri) }
    }

    override suspend fun queueUpload(
        groupId: String,
        target: UploadTarget,
        sourceUri: String,
        file: LocalFileInfo,
    ): AppResult<Unit> = localCall {
        val uid = session.requireUid()
        val fileId = newId()
        val stored = fileStore.fileFor(fileId, file.name)
        withContext(ioDispatcher) {
            stored.parentFile?.mkdirs()
            try {
                pickedFiles.copy(sourceUri, stored)
            } catch (e: FileUnreadableException) {
                stored.parentFile?.deleteRecursively()
                throw e
            }
        }
        val record = FileEntity(
            id = fileId,
            groupId = groupId,
            name = file.name,
            mimeType = file.mimeType,
            sizeBytes = stored.length(),
            storagePath = fileStore.storagePath(groupId, fileId, file.name),
            kind = target.kind,
            chapterId = when (target) {
                is UploadTarget.ChapterDraft -> target.chapterId
                is UploadTarget.FeedbackFile -> target.chapterId
                is UploadTarget.TaskAttachment -> null
            },
            taskId = (target as? UploadTarget.TaskAttachment)?.taskId,
            feedbackId = (target as? UploadTarget.FeedbackFile)?.feedbackId,
            uploadedBy = uid,
            uploadedAt = Instant.now(),
        )
        try {
            when (target) {
                is UploadTarget.ChapterDraft -> recordDraft(record, target)
                is UploadTarget.TaskAttachment ->
                    recordFile(record, ownerExists = localModeDao.task(target.taskId) != null)
                is UploadTarget.FeedbackFile ->
                    recordFile(record, ownerExists = localModeDao.feedback(target.feedbackId) != null)
            }
        } catch (e: Exception) {
            // Nothing points at the copy any more, so it goes too.
            fileStore.delete(listOf(record))
            throw e
        }
    }

    override fun observePendingForChapter(chapterId: String): Flow<List<PendingUpload>> = flowOf(emptyList())

    override fun observePendingForTask(taskId: String): Flow<List<PendingUpload>> = flowOf(emptyList())

    override fun observePendingFeedbackFiles(chapterId: String): Flow<List<PendingUpload>> = flowOf(emptyList())

    override fun observeTaskAttachments(taskId: String): Flow<List<FileAttachment>> =
        fileDao.observeForTask(taskId).map { files -> files.map { it.toDomain() } }

    /** Nothing is ever waiting to upload, so there is nothing to retry or discard. */
    override suspend fun retryUpload(fileId: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun discardUpload(fileId: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun localCopy(file: FileAttachment): AppResult<String> = localCall {
        val stored = fileStore.fileFor(file.id, file.name)
        if (!withContext(ioDispatcher) { stored.exists() }) throw LocalFailure(DomainError.NotFound)
        stored.absolutePath
    }

    /**
     * The next version number comes from the chapter, read and bumped in one
     * transaction. The first draft also moves the chapter from Not started to
     * Drafting; like the cloud build, only the upload goes in the feed then.
     */
    private suspend fun recordDraft(record: FileEntity, target: UploadTarget.ChapterDraft) {
        database.withTransaction {
            val chapter = localModeDao.chapter(target.chapterId) ?: throw LocalFailure(DomainError.NotFound)
            val next = chapter.latestVersion + 1
            fileDao.upsertAll(listOf(record))
            versionDao.upsertAll(
                listOf(
                    ChapterVersionEntity(
                        groupId = record.groupId,
                        chapterId = chapter.id,
                        versionNumber = next,
                        fileId = record.id,
                        note = target.note,
                        uploadedBy = record.uploadedBy,
                        uploadedAt = record.uploadedAt,
                    ),
                ),
            )
            chapterDao.upsertAll(
                listOf(
                    chapter.copy(
                        latestVersion = next,
                        status = if (chapter.status == ChapterStatus.NOT_STARTED) ChapterStatus.DRAFTING else chapter.status,
                        updatedAt = record.uploadedAt,
                    ),
                ),
            )
            activity.record(
                record.groupId,
                ActivityTypes.DRAFT_UPLOADED,
                actorId = record.uploadedBy,
                targetId = chapter.id,
                targetTitle = chapter.title,
                detail = next.toString(),
            )
        }
    }

    /** A task attachment or feedback file, refused if its task or feedback was deleted meanwhile. */
    private suspend fun recordFile(record: FileEntity, ownerExists: Boolean) {
        if (!ownerExists) throw LocalFailure(DomainError.NotFound)
        fileDao.upsertAll(listOf(record))
    }
}
