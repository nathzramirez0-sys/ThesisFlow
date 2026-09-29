package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Chapter writes work offline: Firestore queues them and they sync on reconnect. */
interface ChapterRepository {
    fun observeChapters(groupId: String): Flow<List<Chapter>>

    /** Every chapter of every group the user is in, e.g. for progress on the group list. */
    fun observeAllChapters(): Flow<List<Chapter>>

    fun observeChapter(chapterId: String): Flow<Chapter?>

    /** Newest version first. */
    fun observeVersions(chapterId: String): Flow<List<ChapterVersion>>

    /** Appends a chapter after the existing ones and returns its id. */
    suspend fun addChapter(groupId: String, title: String): AppResult<String>

    /** Adds the standard five chapters, for a group that has none. */
    suspend fun addDefaultChapters(groupId: String): AppResult<Unit>

    suspend fun renameChapter(groupId: String, chapterId: String, title: String): AppResult<Unit>

    /** Deletes the chapter; a Cloud Function then removes its drafts. */
    suspend fun deleteChapter(groupId: String, chapterId: String): AppResult<Unit>

    suspend fun setStatus(groupId: String, chapterId: String, status: ChapterStatus): AppResult<Unit>

    suspend fun setDeadline(groupId: String, chapterId: String, deadline: Instant?): AppResult<Unit>
}
