package com.nathzramirez.thesisflow.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nathzramirez.thesisflow.feature.auth.LoginScreen
import com.nathzramirez.thesisflow.feature.auth.SignUpScreen

/** Signed out of the cloud build: sign in, or create an account. */
@Composable
internal fun SignedOutNavHost(hasPendingInvite: Boolean) {
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
