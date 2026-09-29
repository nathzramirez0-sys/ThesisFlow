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
data object OnboardingRoute

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
data class ChapterListRoute(val groupId: String)

/** [number] is the chapter's position in the list, shown as "Chapter 03". */
@Serializable
data class ChapterDetailRoute(val groupId: String, val chapterId: String, val number: Int)

@Serializable
data class TaskBoardRoute(val groupId: String)

@Serializable
data class TaskDetailRoute(val groupId: String, val taskId: String)

/** [taskId] is null when creating a task. */
@Serializable
data class TaskEditorRoute(val groupId: String, val taskId: String? = null)

@Serializable
data object ProfileRoute
