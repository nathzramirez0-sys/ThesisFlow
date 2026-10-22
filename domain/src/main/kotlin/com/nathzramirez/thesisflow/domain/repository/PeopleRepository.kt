package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.Person
import com.nathzramirez.thesisflow.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The local build's stand-in for accounts. Everything is saved on one phone,
 * and switching person is how a leader, a member and an adviser take turns on
 * it. The cloud build has real accounts and has no implementation of this.
 */
interface PeopleRepository {
    /** Everyone set up on this phone, by name. */
    fun observePeople(): Flow<List<Person>>

    /** Adds a blank person and switches to them, so onboarding asks for their details next. */
    suspend fun addPerson(): AppResult<Unit>

    suspend fun switchTo(uid: String): AppResult<Unit>

    /** Adds a sample thesis group with its leader, member and adviser, and switches to the leader. */
    suspend fun addSampleGroup(): AppResult<Unit>
}
