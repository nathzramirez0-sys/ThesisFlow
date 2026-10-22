package com.nathzramirez.thesisflow.data.localmode

import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.domain.model.User
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Profiles live only in Room; every person on the phone has a row in `users`. */
internal class LocalUserRepository @Inject constructor(
    private val session: LocalSession,
    private val database: ThesisFlowDatabase,
    private val userDao: UserDao,
    private val localModeDao: LocalModeDao,
) : UserRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<User?> = session.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else userDao.observe(uid).map { it?.toDomain() }
    }

    override suspend fun refreshCurrentUser(): AppResult<User> = localCall {
        val user = userDao.get(session.requireUid()) ?: throw LocalFailure(DomainError.NotFound)
        user.toDomain()
    }

    /** Also renames the person in every group, which the cloud build's syncMemberProfiles function does. */
    override suspend fun updateProfile(displayName: String, course: String, school: String): AppResult<Unit> =
        localCall {
            val uid = session.requireUid()
            val user = userDao.get(uid) ?: throw LocalFailure(DomainError.NotFound)
            database.withTransaction {
                userDao.upsert(
                    user.copy(displayName = displayName, course = course, school = school, onboardingComplete = true),
                )
                localModeDao.renameMember(uid, displayName)
            }
        }
}
