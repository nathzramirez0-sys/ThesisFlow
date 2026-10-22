package com.nathzramirez.thesisflow.domain.model

/**
 * Someone using the local build of the app. Everyone in it shares one phone,
 * so picking a person from the list stands in for signing in.
 */
data class Person(
    val uid: String,
    /** Blank until the person finishes setting up their profile. */
    val displayName: String,
    /** Oldest group first. */
    val memberships: List<Membership>,
) {
    data class Membership(val groupName: String, val role: Role)
}
