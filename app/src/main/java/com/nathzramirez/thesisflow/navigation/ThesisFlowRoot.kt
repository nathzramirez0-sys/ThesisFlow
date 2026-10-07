package com.nathzramirez.thesisflow.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.feature.activity.ActivityFeedScreen
import com.nathzramirez.thesisflow.feature.activity.ActivityLink
import com.nathzramirez.thesisflow.feature.auth.LoginScreen
import com.nathzramirez.thesisflow.feature.chapters.detail.ChapterDetailScreen
import com.nathzramirez.thesisflow.feature.chapters.list.ChapterListScreen
import com.nathzramirez.thesisflow.feature.auth.SignUpScreen
import com.nathzramirez.thesisflow.feature.groups.createjoin.CreateJoinGroupScreen
import com.nathzramirez.thesisflow.feature.groups.list.GroupListScreen
import com.nathzramirez.thesisflow.feature.groups.overview.GroupOverviewScreen
import com.nathzramirez.thesisflow.feature.profile.OnboardingScreen
import com.nathzramirez.thesisflow.feature.profile.ProfileScreen
import com.nathzramirez.thesisflow.feature.stats.GroupStatsScreen
import com.nathzramirez.thesisflow.feature.tasks.board.TaskBoardScreen
import com.nathzramirez.thesisflow.feature.tasks.detail.TaskDetailScreen
import com.nathzramirez.thesisflow.feature.tasks.editor.TaskEditorScreen
import com.nathzramirez.thesisflow.notifications.NotificationLink

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
    val pendingNotification by viewModel.pendingNotificationLink.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

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
            SessionState.Ready -> OfflineAware(isOffline) {
                MainNavHost(
                    pendingInviteCode = pendingInvite,
                    onInviteHandled = viewModel::onInviteHandled,
                    pendingNotification = pendingNotification,
                    chapterNumber = viewModel::chapterNumber,
                    onNotificationHandled = viewModel::onNotificationHandled,
                )
            }
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
private fun MainNavHost(
    pendingInviteCode: String?,
    onInviteHandled: () -> Unit,
    pendingNotification: NotificationLink?,
    chapterNumber: suspend (groupId: String, chapterId: String) -> Int?,
    onNotificationHandled: () -> Unit,
) {
    val navController = rememberNavController()

    // A tapped notification opens its screen above the group, so Back leads to the group,
    // then the list. A chapter that isn't on the phone opens the group instead.
    LaunchedEffect(pendingNotification) {
        val link = pendingNotification ?: return@LaunchedEffect
        navController.navigate(GroupOverviewRoute(link.groupId)) {
            popUpTo<GroupListRoute>()
            launchSingleTop = true
        }
        when (link) {
            is NotificationLink.ToChapter -> chapterNumber(link.groupId, link.chapterId)?.let { number ->
                navController.navigate(ChapterDetailRoute(link.groupId, link.chapterId, number))
            }
            is NotificationLink.ToTask -> navController.navigate(TaskDetailRoute(link.groupId, link.taskId))
            is NotificationLink.ToGroup -> Unit
        }
        onNotificationHandled()
    }

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
                onOpenActivity = { navController.navigate(ActivityFeedRoute(route.groupId)) },
                onOpenStats = { navController.navigate(GroupStatsRoute(route.groupId)) },
                onOpenActivityLink = { link -> navController.openActivityLink(route.groupId, link) },
            )
        }
        composable<GroupStatsRoute> { entry ->
            GroupStatsScreen(route = entry.toRoute<GroupStatsRoute>(), onBack = { navController.popBackStack() })
        }
        composable<ActivityFeedRoute> { entry ->
            val route = entry.toRoute<ActivityFeedRoute>()
            ActivityFeedScreen(
                route = route,
                onBack = { navController.popBackStack() },
                onOpen = { link -> navController.openActivityLink(route.groupId, link) },
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

private fun NavController.openActivityLink(groupId: String, link: ActivityLink) = when (link) {
    is ActivityLink.ToChapter -> navigate(ChapterDetailRoute(groupId, link.chapterId, link.number))
    is ActivityLink.ToTask -> navigate(TaskDetailRoute(groupId, link.taskId))
}

/**
 * Puts a slim "offline" strip above the app while there's no network. The strip
 * takes the status bar's space, so the screens below don't pad for it twice.
 */
@Composable
private fun OfflineAware(isOffline: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isOffline, enter = expandVertically(), exit = shrinkVertically()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlowDot(AuroraTheme.colors.statusReview, size = 8.dp)
                Text(
                    stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .then(if (isOffline) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        ) {
            content()
        }
    }
}
