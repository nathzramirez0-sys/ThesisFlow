package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.User
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** The signed-in user's profile from the local cache; null while signed out or not yet loaded. */
    fun observeCurrentUser(): Flow<User?>

    /** Loads the profile from Firestore into the cache, creating the document if it is missing. */
    suspend fun refreshCurrentUser(): AppResult<User>

    /** Saves the profile and marks onboarding complete. */
    suspend fun updateProfile(displayName: String, course: String, school: String): AppResult<Unit>
}
