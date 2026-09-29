package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

data class Group(
    val id: String,
    val name: String,
    val thesisTitle: String,
    val course: String,
    val school: String,
    val myRole: Role,
    val memberCount: Int,
    val proposalDefenseAt: Instant?,
    val finalDefenseAt: Instant?,
    val createdAt: Instant?,
)
