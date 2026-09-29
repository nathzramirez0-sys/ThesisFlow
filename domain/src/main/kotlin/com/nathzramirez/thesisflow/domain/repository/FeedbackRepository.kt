package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Feedback writes work offline: Firestore queues them and they sync on reconnect. */
interface FeedbackRepository {
    /** Newest first, each with its files. */
    fun observeFeedback(chapterId: String): Flow<List<Feedback>>

    fun observeFeedbackById(feedbackId: String): Flow<Feedback?>

    /** Open feedback per chapter id, counted locally; chapters without any are left out. */
    fun observeOpenCounts(groupId: String): Flow<Map<String, Int>>

    /**
     * Posts feedback and returns its id. With [moveToRevisions], the chapter moves
     * to Revisions in the same write, so students never see one without the other.
     */
    suspend fun postFeedback(
        groupId: String,
        chapterId: String,
        body: String,
        versionNumber: Int?,
        moveToRevisions: Boolean,
    ): AppResult<String>

    /** Resolving records who did it and when; reopening clears both. */
    suspend fun setResolved(groupId: String, chapterId: String, feedbackId: String, resolved: Boolean): AppResult<Unit>

    /** Deletes the feedback; a Cloud Function then removes its files. */
    suspend fun deleteFeedback(groupId: String, chapterId: String, feedbackId: String): AppResult<Unit>
}
