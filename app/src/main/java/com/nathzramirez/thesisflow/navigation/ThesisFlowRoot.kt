package com.nathzramirez.thesisflow.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nathzramirez.thesisflow.MainViewModel
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.SessionState
import com.nathzramirez.thesisflow.designsystem.component.AuroraBackground
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.feature.auth.LoginScreen
import com.nathzramirez.thesisflow.feature.chapters.detail.ChapterDetailScreen
import com.nathzramirez.thesisflow.feature.chapters.list.ChapterListScreen
import com.nathzramirez.thesisflow.feature.auth.SignUpScreen
import com.nathzramirez.thesisflow.feature.groups.createjoin.CreateJoinGroupScreen
import com.nathzramirez.thesisflow.feature.groups.list.GroupListScreen
import com.nathzramirez.thesisflow.feature.groups.overview.GroupOverviewScreen
import com.nathzramirez.thesisflow.feature.profile.OnboardingScreen
import com.nathzramirez.thesisflow.feature.profile.ProfileScreen
import com.nathzramirez.thesisflow.feature.tasks.board.TaskBoardScreen
import com.nathzramirez.thesisflow.feature.tasks.detail.TaskDetailScreen
import com.nathzramirez.thesisflow.feature.tasks.editor.TaskEditorScreen

/**
 * Picks a whole navigation graph from the session state. Each graph has its own
 * back stack, so after signing out, Back can't return to a signed-in screen.
 *
 * Every screen lives inside a graph, onboarding included. A screen outside one
 * would get its ViewModel from the Activity, which outlives sign-out: the next
 * account to onboard on the same phone would see the previous person's form.
 */
@Composable
fun ThesisFlowRoot(viewModel: MainViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingInviteCode.collectAsStateWithLifecycle()

    AuroraBackground {
        when (session) {
            SessionState.Loading -> FullScreenLoading()
            SessionState.SignedOut -> AuthNavHost(hasPendingInvite = pendingInvite != null)
            SessionState.NeedsOnboarding -> OnboardingNavHost()
            SessionState.ProfileUnavailable -> MessageScreen(
                title = stringResource(R.string.profile_unavailable_title),
                body = stringResource(R.string.profile_unavailable_body),
            ) {
                GradientButton(text = stringResource(R.string.action_retry), onClick = viewModel::retryProfile)
                GhostButton(text = stringResource(R.string.action_sign_out), onClick = viewModel::signOut)
            }
            SessionState.Ready -> MainNavHost(
                pendingInviteCode = pendingInvite,
                onInviteHandled = viewModel::onInviteHandled,
            )
        }
    }
}

@Composable
private fun AuthNavHost(hasPendingInvite: Boolean) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(
                hasPendingInvite = hasPendingInvite,
                onCreateAccount = { navController.navigate(SignUpRoute) },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** A one-screen graph, so onboarding's ViewModel is new for every account. */
@Composable
private fun OnboardingNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = OnboardingRoute) {
        composable<OnboardingRoute> { OnboardingScreen() }
    }
}

@Composable
private fun MainNavHost(pendingInviteCode: String?, onInviteHandled: () -> Unit) {
    val navController = rememberNavController()

    // An invite link opened at any time lands on the Join tab with its code filled in.
    LaunchedEffect(pendingInviteCode) {
        val code = pendingInviteCode ?: return@LaunchedEffect
        navController.navigate(CreateJoinGroupRoute(inviteCode = code))
        onInviteHandled()
    }

    NavHost(navController = navController, startDestination = GroupListRoute) {
        composable<GroupListRoute> {
            GroupListScreen(
                onGroupClick = { navController.navigate(GroupOverviewRoute(it)) },
                onCreateGroup = { navController.navigate(CreateJoinGroupRoute()) },
                onJoinGroup = { navController.navigate(CreateJoinGroupRoute(startOnJoin = true)) },
                onProfileClick = { navController.navigate(ProfileRoute) },
            )
        }
        composable<CreateJoinGroupRoute> { entry ->
            val route = entry.toRoute<CreateJoinGroupRoute>()
            CreateJoinGroupScreen(
                initialInviteCode = route.inviteCode,
                startOnJoin = route.startOnJoin,
                onBack = { navController.popBackStack() },
                onGroupReady = { groupId ->
                    // Replace the form, so Back from the new group returns to the list.
                    navController.navigate(GroupOverviewRoute(groupId)) {
                        popUpTo<CreateJoinGroupRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<GroupOverviewRoute> { entry ->
            val route = entry.toRoute<GroupOverviewRoute>()
            GroupOverviewScreen(
                route = route,
                onBack = { navController.popBackStack() },
                onOpenChapters = { navController.navigate(ChapterListRoute(route.groupId)) },
                onOpenTasks = { navController.navigate(TaskBoardRoute(route.groupId)) },
            )
        }
        composable<ChapterListRoute> { entry ->
            val route = entry.toRoute<ChapterListRoute>()
            ChapterListScreen(
                route = route,
                onBack = { navController.popBackStack() },
                onChapterClick = { chapterId, number ->
                    navController.navigate(ChapterDetailRoute(route.groupId, chapterId, number))
                },
            )
        }
        composable<ChapterDetailRoute> { entry ->
            ChapterDetailScreen(
                route = entry.toRoute<ChapterDetailRoute>(),
                onBack = { navController.popBackStack() },
            )
        }
        composable<TaskBoardRoute> { entry ->
            val route = entry.toRoute<TaskBoardRoute>()
            TaskBoardScreen(
                route = route,
                onBack = { navController.popBackStack() },
                onTaskClick = { taskId -> navController.navigate(TaskDetailRoute(route.groupId, taskId)) },
                onNewTask = { navController.navigate(TaskEditorRoute(route.groupId)) },
            )
        }
        composable<TaskDetailRoute> { entry ->
            val route = entry.toRoute<TaskDetailRoute>()
            TaskDetailScreen(
                route = route,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(TaskEditorRoute(route.groupId, route.taskId)) },
                onOpenChapter = { chapterId, number ->
                    navController.navigate(ChapterDetailRoute(route.groupId, chapterId, number))
                },
            )
        }
        composable<TaskEditorRoute> { entry ->
            val route = entry.toRoute<TaskEditorRoute>()
            TaskEditorScreen(
                route = route,
                onBack = { navController.popBackStack() },
                onSaved = { taskId ->
                    if (route.taskId == null) {
                        // A new task opens in place of the editor, so Back returns to the board.
                        navController.navigate(TaskDetailRoute(route.groupId, taskId)) {
                            popUpTo<TaskEditorRoute> { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
