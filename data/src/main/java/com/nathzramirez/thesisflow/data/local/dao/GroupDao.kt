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
     * left or was removed from disappear, together with their cached members.
     */
    @Transaction
    suspend fun replaceAll(groups: List<GroupEntity>) {
        if (groups.isEmpty()) deleteAll() else deleteAllExcept(groups.map { it.id })
        upsertAll(groups)
        deleteOrphanMembers()
    }

    /** Removes a group the user just left, without waiting for the listener. */
    @Transaction
    suspend fun deleteWithMembers(groupId: String) {
        delete(groupId)
        deleteOrphanMembers()
    }

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun delete(groupId: String)

    @Query("DELETE FROM groups WHERE id NOT IN (:keepIds)")
    suspend fun deleteAllExcept(keepIds: List<String>)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()

    @Query("DELETE FROM members WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun deleteOrphanMembers()
}
