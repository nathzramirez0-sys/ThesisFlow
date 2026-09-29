package com.nathzramirez.thesisflow.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Invites
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Members
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Users
import com.nathzramirez.thesisflow.domain.model.Invite
import java.time.Instant

/*
 * Firestore documents are mapped by hand instead of with toObject(). There is no
 * reflection, missing fields get explicit defaults, and a malformed document is
 * skipped (null) instead of crashing the sync.
 */

internal fun DocumentSnapshot.toUserEntity(): UserEntity? {
    if (!exists()) return null
    return UserEntity(
        uid = id,
        email = getString(Users.EMAIL).orEmpty(),
        displayName = getString(Users.DISPLAY_NAME).orEmpty(),
        photoUrl = getString(Users.PHOTO_URL),
        course = getString(Users.COURSE).orEmpty(),
        school = getString(Users.SCHOOL).orEmpty(),
        onboardingComplete = getBoolean(Users.ONBOARDING_COMPLETE) ?: false,
    )
}

/** Returns null when [currentUid] has no role in the group, e.g. while a removal is syncing. */
internal fun DocumentSnapshot.toGroupEntity(currentUid: String): GroupEntity? {
    if (!exists()) return null
    val roles = get(Groups.ROLES) as? Map<*, *> ?: return null
    val myRole = roleFromWire(roles[currentUid] as? String) ?: return null
    val memberIds = get(Groups.MEMBER_IDS) as? List<*> ?: emptyList<Any>()
    return GroupEntity(
        id = id,
        name = getString(Groups.NAME).orEmpty(),
        thesisTitle = getString(Groups.THESIS_TITLE).orEmpty(),
        course = getString(Groups.COURSE).orEmpty(),
        school = getString(Groups.SCHOOL).orEmpty(),
        myRole = myRole,
        memberCount = memberIds.size,
        proposalDefenseAt = instant(Groups.PROPOSAL_DEFENSE_AT),
        finalDefenseAt = instant(Groups.FINAL_DEFENSE_AT),
        createdAt = instant(Groups.CREATED_AT),
        updatedAt = instant(Groups.UPDATED_AT),
    )
}

internal fun DocumentSnapshot.toMemberEntity(groupId: String): MemberEntity? {
    if (!exists()) return null
    val role = roleFromWire(getString(Members.ROLE)) ?: return null
    return MemberEntity(
        groupId = groupId,
        uid = id,
        displayName = getString(Members.DISPLAY_NAME).orEmpty(),
        photoUrl = getString(Members.PHOTO_URL),
        role = role,
        joinedAt = instant(Members.JOINED_AT),
    )
}

internal fun DocumentSnapshot.toInvite(): Invite? {
    if (!exists()) return null
    val code = getString(Invites.CODE) ?: return null
    val role = roleFromWire(getString(Invites.ROLE)) ?: return null
    val expiresAt = instant(Invites.EXPIRES_AT) ?: return null
    return Invite(code = code, role = role, expiresAt = expiresAt)
}

/**
 * Reads a timestamp. A server timestamp that hasn't been confirmed yet (an offline
 * write) is estimated from the device clock instead of returned as null.
 */
private fun DocumentSnapshot.instant(field: String): Instant? =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant()
