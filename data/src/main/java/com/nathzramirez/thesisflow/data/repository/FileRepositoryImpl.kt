package com.nathzramirez.thesisflow.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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
import com.nathzramirez.thesisflow.data.upload.UploadScheduler
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.FileKind
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.UploadState
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FileRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val pendingUploads: PendingUploadDao,
    private val fileDao: FileDao,
    private val localFiles: LocalFiles,
    private val scheduler: UploadScheduler,
) : FileRepository {

    override suspend fun inspect(sourceUri: String): AppResult<LocalFileInfo> = safeCall {
        withContext(ioDispatcher) {
            val uri = Uri.parse(sourceUri)
            val resolver = context.contentResolver
            var name: String? = null
            var size = -1L
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0)
                            if (!cursor.isNull(1)) size = cursor.getLong(1)
                        }
                    }
                // Some providers don't report a size; ask the file itself.
                if (size < 0) size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
            } catch (e: IOException) {
                throw FileUnreadableException(e)
            } catch (e: SecurityException) {
                throw FileUnreadableException(e)
            }
            val fileName = LocalFiles.sanitizeName(name ?: uri.lastPathSegment ?: "file")
            val mimeType = resolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            LocalFileInfo(name = fileName, mimeType = mimeType, sizeBytes = size)
        }
    }

    override suspend fun queueDraft(
        groupId: String,
        chapterId: String,
        sourceUri: String,
        file: LocalFileInfo,
        note: String,
    ): AppResult<Unit> = safeCall {
        val uid = auth.requireUid()
        // An id from Firestore's generator, chosen now so every retry targets the same records.
        val fileId = firestore.collection(Groups.COLLECTION).document(groupId).collection(Files.COLLECTION).document().id
        val copy = copyIntoAppStorage(sourceUri, fileId, file.name)

        pendingUploads.insert(
            PendingUploadEntity(
                fileId = fileId,
                groupId = groupId,
                chapterId = chapterId,
                kind = FileKind.DRAFT,
                cachedPath = copy.absolutePath,
                fileName = file.name,
                mimeType = file.mimeType,
                sizeBytes = copy.length(),
                note = note,
                uploadedBy = uid,
                state = UploadState.QUEUED,
                progressPercent = 0,
                failure = null,
                createdAt = Instant.now(),
            ),
        )
        scheduler.enqueue(fileId)
    }

    override fun observePendingUploads(chapterId: String): Flow<List<PendingUpload>> =
        pendingUploads.observeForChapter(chapterId).map { uploads -> uploads.map { it.toDomain() } }

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
                val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                    ?: throw IOException("Could not open $sourceUri")
                input.use { source -> target.outputStream().use { source.copyTo(it) } }
            } catch (e: IOException) {
                dir.deleteRecursively()
                throw FileUnreadableException(e)
            } catch (e: SecurityException) {
                dir.deleteRecursively()
                throw FileUnreadableException(e)
            }
            target
        }
}
