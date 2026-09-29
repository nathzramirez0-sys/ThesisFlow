package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observe(groupId: String): Flow<GroupEntity?>

    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Upsert
    suspend fun upsertAll(groups: List<GroupEntity>)

    /**
     * Makes the table match a complete server snapshot of "my groups": groups the user
     * left or was removed from disappear, together with everything cached under them.
     */
    @Transaction
    suspend fun replaceAll(groups: List<GroupEntity>) {
        if (groups.isEmpty()) deleteAll() else deleteAllExcept(groups.map { it.id })
        upsertAll(groups)
        deleteOrphans()
    }

    /** Removes a group the user just left, without waiting for the listener. */
    @Transaction
    suspend fun deleteWithContent(groupId: String) {
        delete(groupId)
        deleteOrphans()
    }

    /**
     * Group content has no foreign keys (listeners deliver it in any order), so
     * rows whose group is gone are removed here instead of by cascade.
     */
    @Transaction
    suspend fun deleteOrphans() {
        deleteOrphanMembers()
        deleteOrphanChapters()
        deleteOrphanVersions()
        deleteOrphanFiles()
        deleteOrphanPendingUploads()
        deleteOrphanTasks()
        deleteOrphanTaskAssignees()
        deleteOrphanTaskComments()
        deleteOrphanFeedback()
        deleteOrphanActivity()
    }

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun delete(groupId: String)

    @Query("DELETE FROM groups WHERE id NOT IN (:keepIds)")
    suspend fun deleteAllExcept(keepIds: List<String>)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()

    @Query("DELETE FROM members WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanMembers()

    @Query("DELETE FROM chapters WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanChapters()

    @Query("DELETE FROM chapter_versions WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanVersions()

    @Query("DELETE FROM files WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanFiles()

    /** The upload worker notices its row is gone and deletes the local copy. */
    @Query("DELETE FROM pending_uploads WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanPendingUploads()

    @Query("DELETE FROM tasks WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanTasks()

    @Query("DELETE FROM task_assignees WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanTaskAssignees()

    @Query("DELETE FROM task_comments WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanTaskComments()

    @Query("DELETE FROM feedback WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanFeedback()

    @Query("DELETE FROM activity WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanActivity()
}
