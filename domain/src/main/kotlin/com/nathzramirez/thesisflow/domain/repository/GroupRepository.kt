package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Invite
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeMyGroups(): Flow<List<Group>>

    fun observeGroup(groupId: String): Flow<Group?>

    fun observeMembers(groupId: String): Flow<List<Member>>

    /** Active invite codes. Leader-only and online-only, so this is not cached locally. */
    fun observeInvites(groupId: String): Flow<List<Invite>>

    /** Returns the new group's id. Works offline: the write is queued and syncs later. */
    suspend fun createGroup(
        name: String,
        thesisTitle: String,
        course: String,
        school: String,
    ): AppResult<String>

    /** Returns the joined group's id. Needs a connection, because the server checks the code. */
    suspend fun joinGroup(code: String): AppResult<String>

    /** Creates a fresh code for [role] and revokes the previous one. */
    suspend fun createInvite(groupId: String, role: Role): AppResult<Invite>

    suspend fun changeMemberRole(groupId: String, memberUid: String, role: Role): AppResult<Unit>

    suspend fun removeMember(groupId: String, memberUid: String): AppResult<Unit>

    suspend fun leaveGroup(groupId: String): AppResult<Unit>

    /** Deletes the group and everything in it. Leader-only. */
    suspend fun deleteGroup(groupId: String): AppResult<Unit>
}
