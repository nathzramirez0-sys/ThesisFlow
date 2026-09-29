package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

data class Member(
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
    val role: Role,
    val joinedAt: Instant?,
)
