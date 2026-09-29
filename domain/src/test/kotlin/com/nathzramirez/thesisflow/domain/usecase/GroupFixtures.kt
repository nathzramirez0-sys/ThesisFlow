package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role

const val GROUP_ID = "group-1"

fun member(uid: String, role: Role) = Member(
    uid = uid,
    displayName = uid.replaceFirstChar(Char::uppercase),
    photoUrl = null,
    role = role,
    joinedAt = null,
)

fun group(myRole: Role, memberCount: Int) = Group(
    id = GROUP_ID,
    name = "Group 7",
    thesisTitle = "",
    course = "BSCS",
    school = "UPang",
    myRole = myRole,
    memberCount = memberCount,
    proposalDefenseAt = null,
    finalDefenseAt = null,
    createdAt = null,
)
