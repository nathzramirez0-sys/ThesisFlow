package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

/** An active invite code. Only [Role.MEMBER] and [Role.ADVISER] can be invited; leaders are promoted. */
data class Invite(
    val code: String,
    val role: Role,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}
