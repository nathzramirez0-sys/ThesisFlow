package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun signUpWithEmail(email: String, password: String): AppResult<Unit>

    suspend fun signInWithEmail(email: String, password: String): AppResult<Unit>

    /** [idToken] comes from Credential Manager in the UI layer, which needs an Activity. */
    suspend fun signInWithGoogle(idToken: String): AppResult<Unit>

    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    /** Signs out and clears every locally cached row, so the next account starts clean. */
    suspend fun signOut()
}
