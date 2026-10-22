package com.nathzramirez.thesisflow.data.localmode

import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.DefaultChapters
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Invite
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.InviteCodeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Groups for the local build. Everyone's groups share one database, so the list
 * and each group's "my role" are worked out for whoever is using the app now.
 * Invites and joining follow the createInvite and joinGroup functions: a fresh
 * code revokes the old one, codes expire after a week, and a group holds ten.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LocalGroupRepository @Inject constructor(
    private val session: LocalSession,
    private val database: ThesisFlowDatabase,
    private val groupDao: GroupDao,
    private val memberDao: MemberDao,
    private val chapterDao: ChapterDao,
    private val userDao: UserDao,
    private val localModeDao: LocalModeDao,
    private val activity: LocalActivityLog,
    private val cleanup: LocalCleanup,
) : GroupRepository {

    private val random = SecureRandom()

    override fun observeMyGroups(): Flow<List<Group>> = session.currentUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            localModeDao.observeGroupsOf(uid).map { groups -> groups.map { it.toDomain() } }
        }
    }

    override fun observeGroup(groupId: String): Flow<Group?> = session.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else localModeDao.observeGroupOf(groupId, uid).map { it?.toDomain() }
    }

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        memberDao.observeForGroup(groupId).map { members -> members.map { it.toDomain() } }

    override fun observeInvites(groupId: String): Flow<List<Invite>> = session.invites.map { invites ->
        invites.filter { it.groupId == groupId }.map { Invite(code = it.code, role = it.role, expiresAt = it.expiresAt) }
    }

    override suspend fun createGroup(
        name: String,
        thesisTitle: String,
        course: String,
        school: String,
    ): AppResult<String> = localCall {
        val uid = session.requireUid()
        val groupId = newId()
        val now = Instant.now()
        database.withTransaction {
            groupDao.upsert(
                GroupEntity(
                    id = groupId,
                    name = name,
                    thesisTitle = thesisTitle,
                    course = course,
                    school = school,
                    // Stored for the table's sake only: reads work these out per person.
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
                    groupId = groupId,
                    uid = uid,
                    displayName = userDao.get(uid)?.displayName.orEmpty(),
                    photoUrl = null,
                    role = Role.LEADER,
                    joinedAt = now,
                ),
            )
            chapterDao.upsertAll(
                DefaultChapters.titles.mapIndexed { index, title -> newChapterEntity(groupId, title, index + 1, now) },
            )
        }
        groupId
    }

    override suspend fun joinGroup(code: String): AppResult<String> = localCall {
        val uid = session.requireUid()
        val invite = session.findInvite(InviteCodeFormat.normalize(code))
            ?: throw LocalFailure(DomainError.InviteInvalid)
        if (!Instant.now().isBefore(invite.expiresAt)) throw LocalFailure(DomainError.InviteExpired)
        val groupId = invite.groupId
        localModeDao.group(groupId) ?: throw LocalFailure(DomainError.InviteInvalid)
        if (localModeDao.member(groupId, uid) != null) return@localCall groupId
        if (localModeDao.memberCount(groupId) >= MAX_MEMBERS) throw LocalFailure(DomainError.GroupFull)

        memberDao.upsert(
            MemberEntity(
                groupId = groupId,
                uid = uid,
                displayName = userDao.get(uid)?.displayName.orEmpty(),
                photoUrl = null,
                role = invite.role,
                joinedAt = Instant.now(),
            ),
        )
        activity.record(groupId, ActivityTypes.MEMBER_JOINED, actorId = uid, detail = invite.role.toWire())
        groupId
    }

    override suspend fun createInvite(groupId: String, role: Role): AppResult<Invite> = localCall {
        val uid = session.requireUid()
        if (role == Role.LEADER || localModeDao.member(groupId, uid)?.role != Role.LEADER) {
            throw LocalFailure(DomainError.PermissionDenied)
        }
        val taken = session.invites.first().mapTo(HashSet()) { it.code }
        val code = generateSequence { randomCode() }.first { it !in taken }
        val invite = LocalInvite(code = code, groupId = groupId, role = role, expiresAt = Instant.now().plus(INVITE_TTL))
        session.replaceInvite(invite)
        Invite(code = code, role = role, expiresAt = invite.expiresAt)
    }

    override suspend fun changeMemberRole(groupId: String, memberUid: String, role: Role): AppResult<Unit> =
        localCall {
            val member = localModeDao.member(groupId, memberUid) ?: throw LocalFailure(DomainError.NotFound)
            memberDao.upsert(member.copy(role = role))
        }

    override suspend fun removeMember(groupId: String, memberUid: String): AppResult<Unit> = localCall {
        memberDao.delete(groupId, memberUid)
        activity.record(groupId, ActivityTypes.MEMBER_LEFT, actorId = memberUid)
    }

    /** The last person out takes the group with them; nobody would be left to see it. */
    override suspend fun leaveGroup(groupId: String): AppResult<Unit> = localCall {
        val uid = session.requireUid()
        memberDao.delete(groupId, uid)
        if (localModeDao.memberCount(groupId) == 0) {
            cleanup.deleteGroup(groupId)
        } else {
            activity.record(groupId, ActivityTypes.MEMBER_LEFT, actorId = uid)
        }
    }

    override suspend fun setDefenseDate(groupId: String, kind: DefenseKind, at: Instant?): AppResult<Unit> =
        localCall {
            val uid = session.requireUid()
            val group = localModeDao.group(groupId) ?: throw LocalFailure(DomainError.NotFound)
            val now = Instant.now()
            val (before, updated) = when (kind) {
                DefenseKind.PROPOSAL -> group.proposalDefenseAt to group.copy(proposalDefenseAt = at, updatedAt = now)
                DefenseKind.FINAL -> group.finalDefenseAt to group.copy(finalDefenseAt = at, updatedAt = now)
            }
            groupDao.upsert(updated)
            // Like the groupActivity function: a new or moved date is news, a cleared one isn't.
            if (at != null && at != before) {
                activity.record(groupId, ActivityTypes.DEFENSE_SCHEDULED, actorId = uid, detail = kind.toWire())
            }
        }

    override suspend fun deleteGroup(groupId: String): AppResult<Unit> = localCall {
        cleanup.deleteGroup(groupId)
    }

    private fun randomCode(): String = String(
        CharArray(InviteCodeFormat.LENGTH) {
            InviteCodeFormat.ALPHABET[random.nextInt(InviteCodeFormat.ALPHABET.length)]
        },
    )

    private companion object {
        /** Same limits as functions/src/shared.ts. */
        const val MAX_MEMBERS = 10
        val INVITE_TTL: Duration = Duration.ofDays(7)
    }
}
