package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.FeedbackEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskWithAssignees
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.domain.model.Role
import kotlinx.coroutines.flow.Flow

/** One row of "who is in which group", for the local build's people list. */
data class MembershipRow(val uid: String, val groupName: String, val role: Role)

/**
 * Queries only the local build uses. In the cloud build Room holds one account's
 * data and Cloud Functions clean up after deletes. In the local build everyone's
 * data shares the database, so groups are filtered per person here, and the
 * single-row deletes stand in for those functions.
 *
 * It adds no tables, so both builds share one schema.
 */
@Dao
interface LocalModeDao {

    @Query("SELECT * FROM users ORDER BY displayName COLLATE NOCASE")
    fun observeUsers(): Flow<List<UserEntity>>

    @Query(
        """
        SELECT m.uid AS uid, g.name AS groupName, m.role AS role
        FROM members m JOIN groups g ON g.id = m.groupId
        ORDER BY g.createdAt
        """,
    )
    fun observeMemberships(): Flow<List<MembershipRow>>

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteUser(uid: String)

    /** The person's groups, with their own role and the member count worked out per person. */
    @Query(
        """
        SELECT g.id, g.name, g.thesisTitle, g.course, g.school, m.role AS myRole,
            (SELECT COUNT(*) FROM members c WHERE c.groupId = g.id) AS memberCount,
            g.proposalDefenseAt, g.finalDefenseAt, g.createdAt, g.updatedAt
        FROM groups g JOIN members m ON m.groupId = g.id AND m.uid = :uid
        ORDER BY g.createdAt DESC
        """,
    )
    fun observeGroupsOf(uid: String): Flow<List<GroupEntity>>

    @Query(
        """
        SELECT g.id, g.name, g.thesisTitle, g.course, g.school, m.role AS myRole,
            (SELECT COUNT(*) FROM members c WHERE c.groupId = g.id) AS memberCount,
            g.proposalDefenseAt, g.finalDefenseAt, g.createdAt, g.updatedAt
        FROM groups g JOIN members m ON m.groupId = g.id AND m.uid = :uid
        WHERE g.id = :groupId
        """,
    )
    fun observeGroupOf(groupId: String, uid: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun group(groupId: String): GroupEntity?

    @Query("SELECT * FROM members WHERE groupId = :groupId AND uid = :uid")
    suspend fun member(groupId: String, uid: String): MemberEntity?

    @Query("SELECT COUNT(*) FROM members WHERE groupId = :groupId")
    suspend fun memberCount(groupId: String): Int

    /** Keeps the name copies in every group in step with the profile. */
    @Query("UPDATE members SET displayName = :displayName WHERE uid = :uid")
    suspend fun renameMember(uid: String, displayName: String)

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun chapter(chapterId: String): ChapterEntity?

    @Query("DELETE FROM chapters WHERE id = :chapterId")
    suspend fun deleteChapter(chapterId: String)

    @Query("DELETE FROM chapter_versions WHERE chapterId = :chapterId")
    suspend fun deleteVersionsOf(chapterId: String)

    @Query("DELETE FROM feedback WHERE chapterId = :chapterId")
    suspend fun deleteFeedbackOf(chapterId: String)

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun task(taskId: String): TaskWithAssignees?

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM task_assignees WHERE taskId = :taskId")
    suspend fun deleteAssigneesOf(taskId: String)

    @Query("DELETE FROM task_comments WHERE taskId = :taskId")
    suspend fun deleteCommentsOf(taskId: String)

    @Query("DELETE FROM task_comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: String)

    @Query("SELECT * FROM feedback WHERE id = :feedbackId")
    suspend fun feedback(feedbackId: String): FeedbackEntity?

    @Query("DELETE FROM feedback WHERE id = :feedbackId")
    suspend fun deleteFeedback(feedbackId: String)

    /** Drafts and feedback files both carry their chapter's id. */
    @Query("SELECT * FROM files WHERE chapterId = :chapterId")
    suspend fun filesOfChapter(chapterId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE taskId = :taskId")
    suspend fun filesOfTask(taskId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE feedbackId = :feedbackId")
    suspend fun filesOfFeedback(feedbackId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE groupId = :groupId")
    suspend fun filesOfGroup(groupId: String): List<FileEntity>

    @Query("DELETE FROM files WHERE id IN (:fileIds)")
    suspend fun deleteFiles(fileIds: List<String>)
}
