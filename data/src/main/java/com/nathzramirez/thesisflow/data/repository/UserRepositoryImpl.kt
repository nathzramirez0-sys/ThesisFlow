package com.nathzramirez.thesisflow.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.data.remote.NotSignedInException
import com.nathzramirez.thesisflow.data.remote.UserRemoteDataSource
import com.nathzramirez.thesisflow.data.remote.requireUid
import com.nathzramirez.thesisflow.data.remote.safeCall
import com.nathzramirez.thesisflow.data.remote.uidFlow
import com.nathzramirez.thesisflow.domain.model.User
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRemote: UserRemoteDataSource,
    private val userDao: UserDao,
) : UserRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<User?> = auth.uidFlow().flatMapLatest { uid ->
        if (uid == null) flowOf(null) else userDao.observe(uid).map { it?.toDomain() }
    }

    override suspend fun refreshCurrentUser(): AppResult<User> = safeCall {
        val firebaseUser = auth.currentUser ?: throw NotSignedInException()
        val profile = userRemote.fetchOrCreate(firebaseUser)
        userDao.upsert(profile)
        profile.toDomain()
    }

    override suspend fun updateProfile(
        displayName: String,
        course: String,
        school: String,
    ): AppResult<Unit> = safeCall {
        val uid = auth.requireUid()
        userRemote.updateProfile(uid, displayName, course, school)

        // Update the cache directly as well, so the change shows at once even offline.
        val cached = userDao.get(uid) ?: UserEntity(
            uid = uid,
            email = auth.currentUser?.email.orEmpty(),
            displayName = displayName,
            photoUrl = auth.currentUser?.photoUrl?.toString(),
            course = course,
            school = school,
            onboardingComplete = true,
        )
        userDao.upsert(
            cached.copy(
                displayName = displayName,
                course = course,
                school = school,
                onboardingComplete = true,
            ),
        )
    }
}
