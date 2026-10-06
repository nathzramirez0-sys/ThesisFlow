package com.nathzramirez.thesisflow.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nathzramirez.thesisflow.data.di.IoDispatcher
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.push.PushTokenRegistrar
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.remote.uidFlow
import com.nathzramirez.thesisflow.data.upload.LocalFiles
import com.nathzramirez.thesisflow.data.upload.UploadScheduler
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Only signs in and out. Loading or creating the profile happens in one place
 * afterwards (UserRepository.refreshCurrentUser), whichever way the user signed in.
 */
@Singleton
internal class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: ThesisFlowDatabase,
    private val uploadScheduler: UploadScheduler,
    private val localFiles: LocalFiles,
    private val pushTokens: PushTokenRegistrar,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override val authState: Flow<AuthState> = auth.uidFlow().map { uid ->
        if (uid == null) AuthState.SignedOut else AuthState.SignedIn(uid)
    }

    override suspend fun signUpWithEmail(email: String, password: String): AppResult<Unit> = safeCall {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    override suspend fun signInWithEmail(email: String, password: String): AppResult<Unit> = safeCall {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<Unit> = safeCall {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = safeCall {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun signOut() {
        // While still signed in: the rules only let the owner remove the device entry.
        pushTokens.unregisterCurrentUser()
        auth.signOut()
        // Forget the chosen Google account, so the next sign-in shows the account picker again.
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear credential state", e)
        }
        // Nothing of this account may be left for the next one: queued uploads,
        // cached rows, or downloaded files.
        uploadScheduler.cancelAll()
        withContext(ioDispatcher) {
            database.clearAllTables()
            localFiles.deleteAll()
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
