package com.nathzramirez.thesisflow.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nathzramirez.thesisflow.feature.people.PeopleScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object PeopleRoute

/**
 * Nobody chosen yet in the local build: the people list stands in for sign-in.
 * It has no invite links, so [hasPendingInvite] is never true here.
 */
@Composable
internal fun SignedOutNavHost(@Suppress("UNUSED_PARAMETER") hasPendingInvite: Boolean) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PeopleRoute) {
        composable<PeopleRoute> { PeopleScreen() }
    }
}
