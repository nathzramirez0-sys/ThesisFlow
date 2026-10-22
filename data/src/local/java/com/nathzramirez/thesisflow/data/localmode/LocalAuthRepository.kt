package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The local build has no accounts: whoever is picked on the people list is
 * signed in. Email and Google sign-in belong to the cloud build, whose screens
 * the local build never shows.
 */
internal class LocalAuthRepository @Inject constructor(
    private val session: LocalSession,
    private val userDao: UserDao,
    private val localModeDao: LocalModeDao,
) : AuthRepository {

    override val authState: Flow<AuthState> = session.currentUid.map { uid ->
        if (uid == null) AuthState.SignedOut else AuthState.SignedIn(uid)
    }

    override suspend fun signUpWithEmail(email: String, password: String): AppResult<Unit> = notInLocalBuild()

    override suspend fun signInWithEmail(email: String, password: String): AppResult<Unit> = notInLocalBuild()

    override suspend fun signInWithGoogle(idToken: String): AppResult<Unit> = notInLocalBuild()

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = notInLocalBuild()

    /**
     * Back to the people list. Unlike the cloud build, nothing is cleared: the data
     * belongs to everyone on the phone. Someone added but never set up is removed,
     * after switching away from them, so no screen sees their profile vanish.
     */
    override suspend fun signOut() {
        val uid = session.currentUid.first() ?: return
        session.switchTo(null)
        if (userDao.get(uid)?.onboardingComplete == false) localModeDao.deleteUser(uid)
    }

    private fun notInLocalBuild(): AppResult<Unit> =
        AppResult.Failure(DomainError.Unknown("Accounts are only in the cloud build"))
}
