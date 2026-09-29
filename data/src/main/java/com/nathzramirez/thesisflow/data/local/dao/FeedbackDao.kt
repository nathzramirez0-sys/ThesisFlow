package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nathzramirez.thesisflow.data.local.entity.ActivityEntity
import com.nathzramirez.thesisflow.data.local.entity.FeedbackEntity
import com.nathzramirez.thesisflow.data.local.entity.FeedbackWithDetails
import com.nathzramirez.thesisflow.data.local.entity.OpenFeedbackCount
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    /** Newest first, each with its files and resolver. */
    @Transaction
    @Query("SELECT * FROM feedback WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    fun observeForChapter(chapterId: String): Flow<List<FeedbackWithDetails>>

    @Transaction
    @Query("SELECT * FROM feedback WHERE id = :feedbackId")
    fun observe(feedbackId: String): Flow<FeedbackWithDetails?>

    /** Counted in SQL from the synced rows, so there is no stored counter to drift. */
    @Query(
        """
        SELECT chapterId, COUNT(*) AS openCount FROM feedback
        WHERE groupId = :groupId AND resolved = 0
        GROUP BY chapterId
        """,
    )
    fun observeOpenCounts(groupId: String): Flow<List<OpenFeedbackCount>>

    @Upsert
    suspend fun upsertAll(feedback: List<FeedbackEntity>)

    @Transaction
    suspend fun replaceForGroup(groupId: String, feedback: List<FeedbackEntity>) {
        deleteForGroup(groupId)
        upsertAll(feedback)
    }

    @Query("DELETE FROM feedback WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity WHERE groupId = :groupId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(groupId: String, limit: Int): Flow<List<ActivityEntity>>

    @Upsert
    suspend fun upsertAll(activity: List<ActivityEntity>)

    /** The server snapshot holds the latest entries only, so older ones drop off here. */
    @Transaction
    suspend fun replaceForGroup(groupId: String, activity: List<ActivityEntity>) {
        deleteForGroup(groupId)
        upsertAll(activity)
    }

    @Query("DELETE FROM activity WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
