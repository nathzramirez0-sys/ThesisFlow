package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.domain.model.Person
import com.nathzramirez.thesisflow.domain.repository.PeopleRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

internal class LocalPeopleRepository @Inject constructor(
    private val session: LocalSession,
    private val userDao: UserDao,
    private val localModeDao: LocalModeDao,
    private val sampleGroup: SampleGroup,
) : PeopleRepository {

    override fun observePeople(): Flow<List<Person>> =
        combine(localModeDao.observeUsers(), localModeDao.observeMemberships()) { users, memberships ->
            val byPerson = memberships.groupBy { it.uid }
            users.map { user ->
                Person(
                    uid = user.uid,
                    displayName = user.displayName,
                    memberships = byPerson[user.uid].orEmpty().map { Person.Membership(it.groupName, it.role) },
                )
            }
        }

    override suspend fun addPerson(): AppResult<Unit> = localCall {
        val uid = newId()
        userDao.upsert(
            UserEntity(
                uid = uid,
                email = "",
                displayName = "",
                photoUrl = null,
                course = "",
                school = "",
                onboardingComplete = false,
            ),
        )
        session.switchTo(uid)
    }

    override suspend fun switchTo(uid: String): AppResult<Unit> = localCall {
        userDao.get(uid) ?: throw LocalFailure(DomainError.NotFound)
        session.switchTo(uid)
    }

    override suspend fun addSampleGroup(): AppResult<Unit> = localCall {
        session.switchTo(sampleGroup.create())
    }
}
