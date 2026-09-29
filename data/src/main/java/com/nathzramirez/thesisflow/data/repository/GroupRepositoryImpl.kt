package com.nathzramirez.thesisflow.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.functions.FirebaseFunctions
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Functions
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Invites
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Members
import com.nathzramirez.thesisflow.data.remote.awaitOrQueued
import com.nathzramirez.thesisflow.data.remote.newChapterFields
import com.nathzramirez.thesisflow.data.remote.requireUid
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.remote.toInvite
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.DefaultChapters
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Invite
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GroupRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val userDao: UserDao,
    private val groupDao: GroupDao,
    private val memberDao: MemberDao,
) : GroupRepository {

    private fun groupRef(groupId: String) = firestore.collection(Groups.COLLECTION).document(groupId)

    private fun memberRef(groupId: String, uid: String) =
        groupRef(groupId).collection(Members.COLLECTION).document(uid)

    /**
     * A Cloud Function call with a 30 s limit instead of the SDK's 70 s default:
     * long enough for a cold start, short enough that a stuck request turns into
     * a "check your connection" message instead of a minute-long spinner.
     */
    private fun callable(name: String) =
        functions.getHttpsCallable(name).apply { setTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS) }

    override fun observeMyGroups(): Flow<List<Group>> =
        groupDao.observeAll().map { groups -> groups.map { it.toDomain() } }

    override fun observeGroup(groupId: String): Flow<Group?> =
        groupDao.observe(groupId).map { it?.toDomain() }

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        memberDao.observeForGroup(groupId).map { members -> members.map { it.toDomain() } }

    override fun observeInvites(groupId: String): Flow<List<Invite>> =
        groupRef(groupId).collection(Invites.COLLECTION).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toInvite() } }
            .catch { emit(emptyList()) }

    /**
     * One atomic batch writes the group, the creator's member entry and the five
     * standard chapters. The rules check them together: the creator must be the
     * only member, with the leader role.
     */
    override suspend fun createGroup(
        name: String,
        thesisTitle: String,
        course: String,
        school: String,
    ): AppResult<String> = safeCall {
        val uid = auth.requireUid()
        val profile = userDao.get(uid)
        val group = firestore.collection(Groups.COLLECTION).document()
        val batch = firestore.batch()

        batch
            .set(
                group,
                mapOf(
                    Groups.NAME to name,
                    Groups.THESIS_TITLE to thesisTitle,
                    Groups.COURSE to course,
                    Groups.SCHOOL to school,
                    Groups.MEMBER_IDS to listOf(uid),
                    Groups.ROLES to mapOf(uid to Role.LEADER.toWire()),
                    Groups.CREATED_BY to uid,
                    Groups.CREATED_AT to FieldValue.serverTimestamp(),
                    Groups.UPDATED_AT to FieldValue.serverTimestamp(),
                    Groups.PROPOSAL_DEFENSE_AT to null,
                    Groups.FINAL_DEFENSE_AT to null,
                ),
            )
            .set(
                memberRef(group.id, uid),
                mapOf(
                    Members.DISPLAY_NAME to profile?.displayName.orEmpty(),
                    Members.PHOTO_URL to profile?.photoUrl,
                    Members.ROLE to Role.LEADER.toWire(),
                    Members.JOINED_AT to FieldValue.serverTimestamp(),
                ),
            )
        DefaultChapters.titles.forEachIndexed { index, title ->
            batch.set(group.collection(Chapters.COLLECTION).document(), newChapterFields(title, index + 1, uid))
        }
        batch.commit().awaitOrQueued()

        // Cache now, so the group screen has data even before the listener reports it.
        val now = Instant.now()
        groupDao.upsert(
            GroupEntity(
                id = group.id,
                name = name,
                thesisTitle = thesisTitle,
                course = course,
                school = school,
                myRole = Role.LEADER,
                memberCount = 1,
                proposalDefenseAt = null,
                finalDefenseAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        memberDao.upsert(
            MemberEntity(
                groupId = group.id,
                uid = uid,
                displayName = profile?.displayName.orEmpty(),
                photoUrl = profile?.photoUrl,
                role = Role.LEADER,
                joinedAt = now,
            ),
        )
        group.id
    }

    /**
     * Joining goes through a Cloud Function: a non-member can't read the group, and
     * letting clients add themselves to `memberIds` would let anyone join any group.
     * The listener then adds the group to Room.
     */
    override suspend fun joinGroup(code: String): AppResult<String> = safeCall {
        val result = callable(Functions.JOIN_GROUP)
            .call(mapOf("code" to code))
            .await()
        val data = result.getData() as? Map<*, *>
        checkNotNull(data?.get("groupId") as? String) { "joinGroup returned no groupId" }
    }

    override suspend fun createInvite(groupId: String, role: Role): AppResult<Invite> = safeCall {
        val result = callable(Functions.CREATE_INVITE)
            .call(mapOf("groupId" to groupId, "role" to role.toWire()))
            .await()
        val data = checkNotNull(result.getData() as? Map<*, *>) { "createInvite returned no data" }
        Invite(
            code = data["code"] as String,
            role = role,
            expiresAt = Instant.ofEpochMilli((data["expiresAt"] as Number).toLong()),
        )
    }

    /** The group's `roles` map is what the rules check; the member doc copy is for display. */
    override suspend fun changeMemberRole(
        groupId: String,
        memberUid: String,
        role: Role,
    ): AppResult<Unit> = safeCall {
        firestore.batch()
            .update(
                groupRef(groupId),
                mapOf(
                    "${Groups.ROLES}.$memberUid" to role.toWire(),
                    Groups.UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .update(memberRef(groupId, memberUid), Members.ROLE, role.toWire())
            .commit()
            .awaitOrQueued()
    }

    override suspend fun removeMember(groupId: String, memberUid: String): AppResult<Unit> = safeCall {
        removeFromGroup(groupId, memberUid)
        memberDao.delete(groupId, memberUid)
    }

    override suspend fun leaveGroup(groupId: String): AppResult<Unit> = safeCall {
        removeFromGroup(groupId, auth.requireUid())
        groupDao.deleteWithContent(groupId)
    }

    override suspend fun deleteGroup(groupId: String): AppResult<Unit> = safeCall {
        callable(Functions.DELETE_GROUP)
            .call(mapOf("groupId" to groupId))
            .await()
        groupDao.deleteWithContent(groupId)
    }

    private suspend fun removeFromGroup(groupId: String, uid: String) {
        firestore.batch()
            .update(
                groupRef(groupId),
                mapOf(
                    Groups.MEMBER_IDS to FieldValue.arrayRemove(uid),
                    "${Groups.ROLES}.$uid" to FieldValue.delete(),
                    Groups.UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .delete(memberRef(groupId, uid))
            .commit()
            .awaitOrQueued()
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 30L
    }
}
