package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    /** Leaders first, then members, then advisers; alphabetical within each role. */
    @Query(
        """
        SELECT * FROM members WHERE groupId = :groupId
        ORDER BY CASE role WHEN 'LEADER' THEN 0 WHEN 'MEMBER' THEN 1 ELSE 2 END,
                 displayName COLLATE NOCASE
        """,
    )
    fun observeForGroup(groupId: String): Flow<List<MemberEntity>>

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Upsert
    suspend fun upsertAll(members: List<MemberEntity>)

    /** Makes one group's rows match a complete server snapshot of its members. */
    @Transaction
    suspend fun replaceForGroup(groupId: String, members: List<MemberEntity>) {
        deleteForGroup(groupId)
        upsertAll(members)
    }

    @Query("DELETE FROM members WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query("DELETE FROM members WHERE groupId = :groupId AND uid = :uid")
    suspend fun delete(groupId: String, uid: String)
}
