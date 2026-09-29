package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nathzramirez.thesisflow.data.local.entity.PendingUploadEntity
import com.nathzramirez.thesisflow.domain.model.UploadState
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUploadDao {
    /** Drafts only; files on the chapter's feedback have their own query. */
    @Query("SELECT * FROM pending_uploads WHERE chapterId = :chapterId AND kind = 'DRAFT' ORDER BY createdAt")
    fun observeForChapter(chapterId: String): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads WHERE chapterId = :chapterId AND kind = 'FEEDBACK' ORDER BY createdAt")
    fun observeFeedbackFilesForChapter(chapterId: String): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads WHERE taskId = :taskId ORDER BY createdAt")
    fun observeForTask(taskId: String): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads WHERE fileId = :fileId")
    suspend fun get(fileId: String): PendingUploadEntity?

    @Insert
    suspend fun insert(upload: PendingUploadEntity)

    @Query("UPDATE pending_uploads SET state = :state, progressPercent = :progress WHERE fileId = :fileId")
    suspend fun updateProgress(fileId: String, state: UploadState, progress: Int)

    @Query("UPDATE pending_uploads SET state = 'FAILED', failure = :failure WHERE fileId = :fileId")
    suspend fun markFailed(fileId: String, failure: String)

    @Query("UPDATE pending_uploads SET state = 'QUEUED', progressPercent = 0, failure = NULL WHERE fileId = :fileId")
    suspend fun resetForRetry(fileId: String)

    @Query("DELETE FROM pending_uploads WHERE fileId = :fileId")
    suspend fun delete(fileId: String)
}
