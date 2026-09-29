package com.nathzramirez.thesisflow.navigation

import kotlinx.serialization.Serializable

/*
 * Type-safe routes: arguments are class properties, so a wrong or missing
 * argument is a compile error instead of a crash at runtime.
 */

@Serializable
data object LoginRoute

@Serializable
data object SignUpRoute

@Serializable
data object GroupListRoute

@Serializable
data class CreateJoinGroupRoute(
    val inviteCode: String? = null,
    val startOnJoin: Boolean = false,
)

@Serializable
data class GroupOverviewRoute(val groupId: String)

@Serializable
data object ProfileRoute
