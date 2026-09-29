package com.nathzramirez.thesisflow.domain.model

enum class Role {
    LEADER,
    MEMBER,
    ADVISER;

    /** Mirrors the Firestore rules, so the UI only offers actions the server will accept. */
    val canManageGroup: Boolean get() = this == LEADER
    val canInvite: Boolean get() = this == LEADER
}
