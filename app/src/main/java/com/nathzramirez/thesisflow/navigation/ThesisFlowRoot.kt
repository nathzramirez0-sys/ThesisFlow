package com.nathzramirez.thesisflow.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nathzramirez.thesisflow.MainViewModel
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.SessionState
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.feature.auth.LoginScreen
import com.nathzramirez.thesisflow.feature.auth.SignUpScreen
import com.nathzramirez.thesisflow.feature.groups.createjoin.CreateJoinGroupScreen
import com.nathzramirez.thesisflow.feature.groups.list.GroupListScreen
import com.nathzramirez.thesisflow.feature.groups.overview.GroupOverviewScreen
import com.nathzramirez.thesisflow.feature.profile.OnboardingScreen
import com.nathzramirez.thesisflow.feature.profile.ProfileScreen

/**
 * Picks a whole navigation graph from the session state. Each graph has its own
 * back stack, so after signing out, Back can't return to a signed-in screen.
 */
@Composable
fun ThesisFlowRoot(viewModel: MainViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingInviteCode.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (session) {
            SessionState.Loading -> FullScreenLoading()
            SessionState.SignedOut -> AuthNavHost(hasPendingInvite = pendingInvite != null)
            SessionState.NeedsOnboarding -> OnboardingScreen()
            SessionState.ProfileUnavailable -> MessageScreen(
                title = stringResource(R.string.profile_unavailable_title),
                body = stringResource(R.string.profile_unavailable_body),
            ) {
                Button(onClick = viewModel::retryProfile) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.action_sign_out)) }
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
        composable<GroupOverviewRoute> {
            GroupOverviewScreen(onBack = { navController.popBackStack() })
        }
        composable<ProfileRoute> {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
