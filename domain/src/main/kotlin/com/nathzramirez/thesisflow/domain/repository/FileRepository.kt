package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Files picked on the phone are identified by a content URI string, which keeps
 * this interface free of Android types.
 */
interface FileRepository {
    /** Reads the picked file's name, type and size without copying it. */
    suspend fun inspect(sourceUri: String): AppResult<LocalFileInfo>

    /**
     * Copies the file into app storage and queues it. The upload runs in the
     * background, survives the app closing, and waits for a connection.
     */
    suspend fun queueDraft(
        groupId: String,
        chapterId: String,
        sourceUri: String,
        file: LocalFileInfo,
        note: String,
    ): AppResult<Unit>

    fun observePendingUploads(chapterId: String): Flow<List<PendingUpload>>

    suspend fun retryUpload(fileId: String): AppResult<Unit>

    suspend fun discardUpload(fileId: String): AppResult<Unit>

    /** Returns the path of a local copy, downloading it first if needed. */
    suspend fun localCopy(file: FileAttachment): AppResult<String>
}
