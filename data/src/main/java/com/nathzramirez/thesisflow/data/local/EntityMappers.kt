package com.nathzramirez.thesisflow.data.local

import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.User

internal fun UserEntity.toDomain() = User(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    course = course,
    school = school,
    onboardingComplete = onboardingComplete,
)

internal fun GroupEntity.toDomain() = Group(
    id = id,
    name = name,
    thesisTitle = thesisTitle,
    course = course,
    school = school,
    myRole = myRole,
    memberCount = memberCount,
    proposalDefenseAt = proposalDefenseAt,
    finalDefenseAt = finalDefenseAt,
    createdAt = createdAt,
)

internal fun MemberEntity.toDomain() = Member(
    uid = uid,
    displayName = displayName,
    photoUrl = photoUrl,
    role = role,
    joinedAt = joinedAt,
)
