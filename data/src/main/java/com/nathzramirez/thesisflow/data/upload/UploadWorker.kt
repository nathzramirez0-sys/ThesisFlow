package com.nathzramirez.thesisflow.data.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import com.nathzramirez.thesisflow.data.local.dao.PendingUploadDao
import com.nathzramirez.thesisflow.data.local.entity.PendingUploadEntity
import com.nathzramirez.thesisflow.data.local.entity.UploadFailure
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Files
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Storage
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Versions
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.FileKind
import com.nathzramirez.thesisflow.domain.model.UploadState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Sends one pending upload: the file to Cloud Storage, then its records to Firestore.
 *
 * Every step is safe to repeat. The file id was fixed when the upload was queued,
 * so a retry overwrites the same Storage object, and the Firestore transaction
 * skips its writes if an earlier attempt already made them. A draft never turns
 * into two versions, however often WorkManager retries.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingUploads: PendingUploadDao,
    private val localFiles: LocalFiles,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fileId = inputData.getString(KEY_FILE_ID) ?: return Result.failure()
        val upload = pendingUploads.get(fileId)
        if (upload == null) {
            // Discarded, or the user left the group: nothing to send, just tidy up.
            localFiles.pendingDir(fileId).deleteRecursively()
            return Result.success()
        }
        val uid = auth.currentUser?.uid
        if (uid == null || uid != upload.uploadedBy) return fail(upload, UploadFailure.PERMISSION_DENIED)

        val file = File(upload.cachedPath)
        if (!file.exists()) return fail(upload, UploadFailure.FILE_MISSING)

        return try {
            uploadToStorage(upload, file)
            recordInFirestore(upload)
            finish(upload, file)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val permanent = permanentFailure(e)
            when {
                permanent != null -> fail(upload, permanent)
                runAttemptCount + 1 >= MAX_ATTEMPTS -> fail(upload, UploadFailure.UNKNOWN)
                else -> {
                    Log.w(TAG, "Upload of $fileId failed, will retry", e)
                    pendingUploads.updateProgress(fileId, UploadState.QUEUED, 0)
                    Result.retry()
                }
            }
        }
    }

    private suspend fun uploadToStorage(upload: PendingUploadEntity, file: File) = coroutineScope {
        val metadata = storageMetadata {
            contentType = upload.mimeType
            setCustomMetadata(Storage.METADATA_UPLOADED_BY, upload.uploadedBy)
            setCustomMetadata(Storage.METADATA_KIND, upload.kind.toWire())
        }
        val progress = MutableStateFlow(0)
        // StateFlow drops intermediate values, so Room sees at most a few writes per second.
        val reporter = launch {
            progress.collect { pendingUploads.updateProgress(upload.fileId, UploadState.UPLOADING, it) }
        }
        val task = storage.reference.child(storagePath(upload)).putFile(Uri.fromFile(file), metadata)
        task.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount.coerceAtLeast(1)
            progress.value = (snapshot.bytesTransferred * 100 / total).toInt()
        }
        task.await()
        reporter.cancel()
    }

    /**
     * Drafts need a transaction: the version number comes from the chapter, and two
     * members uploading at once must get v3 and v4, never two v3s.
     */
    private suspend fun recordInFirestore(upload: PendingUploadEntity) {
        val group = firestore.collection(Groups.COLLECTION).document(upload.groupId)
        val fileRef = group.collection(Files.COLLECTION).document(upload.fileId)
        val fileFields = mapOf(
            Files.NAME to upload.fileName,
            Files.MIME_TYPE to upload.mimeType,
            Files.SIZE_BYTES to upload.sizeBytes,
            Files.STORAGE_PATH to storagePath(upload),
            Files.KIND to upload.kind.toWire(),
            Files.CHAPTER_ID to upload.chapterId,
            Files.TASK_ID to null,
            Files.FEEDBACK_ID to null,
            Files.UPLOADED_BY to upload.uploadedBy,
            Files.UPLOADED_AT to FieldValue.serverTimestamp(),
        )

        if (upload.kind != FileKind.DRAFT) {
            fileRef.set(fileFields).await()
            return
        }

        val chapterRef = group.collection(Chapters.COLLECTION).document(checkNotNull(upload.chapterId))
        firestore.runTransaction { transaction ->
            if (transaction.get(fileRef).exists()) return@runTransaction // An earlier attempt got this far.
            val chapter = transaction.get(chapterRef)
            if (!chapter.exists()) throw ChapterDeletedException()

            val next = (chapter.getLong(Chapters.LATEST_VERSION) ?: 0) + 1
            val chapterChanges = mutableMapOf<String, Any>(
                Chapters.LATEST_VERSION to next,
                Chapters.UPDATED_AT to FieldValue.serverTimestamp(),
                Chapters.UPDATED_BY to upload.uploadedBy,
            )
            // The first draft means writing has started.
            if (chapter.getString(Chapters.STATUS) == ChapterStatus.NOT_STARTED.toWire()) {
                chapterChanges[Chapters.STATUS] = ChapterStatus.DRAFTING.toWire()
            }

            transaction.set(fileRef, fileFields)
            transaction.set(
                chapterRef.collection(Versions.COLLECTION).document(next.toString()),
                mapOf(
                    Versions.GROUP_ID to upload.groupId,
                    Versions.CHAPTER_ID to upload.chapterId,
                    Versions.VERSION_NUMBER to next,
                    Versions.FILE_ID to upload.fileId,
                    Versions.NOTE to upload.note,
                    Versions.UPLOADED_BY to upload.uploadedBy,
                    Versions.UPLOADED_AT to FieldValue.serverTimestamp(),
                ),
            )
            transaction.update(chapterRef, chapterChanges)
        }.await()
    }

    /** Keeps the uploaded file as a downloaded copy, so opening it later needs no download. */
    private suspend fun finish(upload: PendingUploadEntity, file: File) {
        val cached = localFiles.downloaded(upload.fileId, upload.fileName)
        cached.parentFile?.mkdirs()
        if (!file.renameTo(cached)) file.copyTo(cached, overwrite = true)
        localFiles.pendingDir(upload.fileId).deleteRecursively()
        pendingUploads.delete(upload.fileId)
    }

    private suspend fun fail(upload: PendingUploadEntity, reason: UploadFailure): Result {
        pendingUploads.markFailed(upload.fileId, reason.name)
        return Result.failure()
    }

    /** Errors retrying can't fix. Anything else (mostly network) is retried with backoff. */
    private fun permanentFailure(e: Exception): UploadFailure? = when {
        e is ChapterDeletedException -> UploadFailure.CHAPTER_DELETED
        e is StorageException && e.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
            UploadFailure.PERMISSION_DENIED
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            UploadFailure.PERMISSION_DENIED
        else -> null
    }

    private fun storagePath(upload: PendingUploadEntity) =
        Storage.filePath(upload.groupId, upload.fileId, upload.fileName)

    private class ChapterDeletedException : IllegalStateException("Chapter was deleted")

    companion object {
        const val KEY_FILE_ID = "fileId"
        private const val MAX_ATTEMPTS = 8
        private const val TAG = "UploadWorker"
    }
}
