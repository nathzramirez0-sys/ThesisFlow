package com.nathzramirez.thesisflow.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.nathzramirez.thesisflow.data.di.IoDispatcher
import com.nathzramirez.thesisflow.data.local.dao.FileDao
import com.nathzramirez.thesisflow.data.local.dao.PendingUploadDao
import com.nathzramirez.thesisflow.data.local.entity.PendingUploadEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.FileUnreadableException
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Files
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Storage
import com.nathzramirez.thesisflow.data.remote.requireUid
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.upload.LocalFiles
import com.nathzramirez.thesisflow.data.upload.PickedFiles
import com.nathzramirez.thesisflow.data.upload.UploadScheduler
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.model.UploadState
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FileRepositoryImpl @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val pendingUploads: PendingUploadDao,
    private val fileDao: FileDao,
    private val localFiles: LocalFiles,
    private val pickedFiles: PickedFiles,
    private val scheduler: UploadScheduler,
) : FileRepository {

    override suspend fun inspect(sourceUri: String): AppResult<LocalFileInfo> = safeCall {
        withContext(ioDispatcher) { pickedFiles.inspect(sourceUri) }
    }

    override suspend fun queueUpload(
        groupId: String,
        target: UploadTarget,
        sourceUri: String,
        file: LocalFileInfo,
    ): AppResult<Unit> = safeCall {
        val uid = auth.requireUid()
        // An id from Firestore's generator, chosen now so every retry targets the same records.
        val fileId = firestore.collection(Groups.COLLECTION).document(groupId).collection(Files.COLLECTION).document().id
        val copy = copyIntoAppStorage(sourceUri, fileId, file.name)

        pendingUploads.insert(
            PendingUploadEntity(
                fileId = fileId,
                groupId = groupId,
                chapterId = when (target) {
                    is UploadTarget.ChapterDraft -> target.chapterId
                    is UploadTarget.FeedbackFile -> target.chapterId
                    is UploadTarget.TaskAttachment -> null
                },
                taskId = (target as? UploadTarget.TaskAttachment)?.taskId,
                feedbackId = (target as? UploadTarget.FeedbackFile)?.feedbackId,
                kind = target.kind,
                cachedPath = copy.absolutePath,
                fileName = file.name,
                mimeType = file.mimeType,
                sizeBytes = copy.length(),
                note = (target as? UploadTarget.ChapterDraft)?.note.orEmpty(),
                uploadedBy = uid,
                state = UploadState.QUEUED,
                progressPercent = 0,
                failure = null,
                createdAt = Instant.now(),
            ),
        )
        scheduler.enqueue(fileId)
    }

    override fun observePendingForChapter(chapterId: String): Flow<List<PendingUpload>> =
        pendingUploads.observeForChapter(chapterId).map { uploads -> uploads.map { it.toDomain() } }

    override fun observePendingForTask(taskId: String): Flow<List<PendingUpload>> =
        pendingUploads.observeForTask(taskId).map { uploads -> uploads.map { it.toDomain() } }

    override fun observePendingFeedbackFiles(chapterId: String): Flow<List<PendingUpload>> =
        pendingUploads.observeFeedbackFilesForChapter(chapterId).map { uploads -> uploads.map { it.toDomain() } }

    override fun observeTaskAttachments(taskId: String): Flow<List<FileAttachment>> =
        fileDao.observeForTask(taskId).map { files -> files.map { it.toDomain() } }

    override suspend fun retryUpload(fileId: String): AppResult<Unit> = safeCall {
        pendingUploads.resetForRetry(fileId)
        scheduler.enqueue(fileId)
    }

    override suspend fun discardUpload(fileId: String): AppResult<Unit> = safeCall {
        scheduler.cancel(fileId)
        pendingUploads.delete(fileId)
        withContext(ioDispatcher) { localFiles.pendingDir(fileId).deleteRecursively() }
    }

    /**
     * Downloaded copies are kept in the cache, so a file opened once opens again
     * offline. The download goes to a temporary name first, so an interrupted
     * download never looks like a complete file.
     */
    override suspend fun localCopy(file: FileAttachment): AppResult<String> = safeCall {
        val target = localFiles.downloaded(file.id, file.name)
        if (!target.exists() || target.length() != file.sizeBytes) {
            val storagePath = fileDao.get(file.id)?.storagePath ?: Storage.filePath(file.groupId, file.id, file.name)
            val partial = File(target.path + ".part")
            withContext(ioDispatcher) { target.parentFile?.mkdirs() }
            storage.reference.child(storagePath).getFile(partial).await()
            withContext(ioDispatcher) {
                if (!partial.renameTo(target)) partial.copyTo(target, overwrite = true).also { partial.delete() }
            }
        }
        target.absolutePath
    }

    /** The picker's read permission can expire, so the upload works from our own copy. */
    private suspend fun copyIntoAppStorage(sourceUri: String, fileId: String, name: String): File =
        withContext(ioDispatcher) {
            val dir = localFiles.pendingDir(fileId).apply { mkdirs() }
            val target = File(dir, name)
            try {
                pickedFiles.copy(sourceUri, target)
            } catch (e: FileUnreadableException) {
                dir.deleteRecursively()
                throw e
            }
            target
        }
}
