package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.ChangeMemberRoleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeMemberRoleUseCaseTest {

    private val repository = mockk<GroupRepository>()
    private val changeRole = ChangeMemberRoleUseCase(repository)

    private fun givenMembers(vararg members: Member) {
        every { repository.observeMembers(GROUP_ID) } returns flowOf(members.toList())
        coEvery { repository.changeMemberRole(any(), any(), any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun `the only leader cannot be demoted`() = runTest {
        givenMembers(member("ana", Role.LEADER), member("ben", Role.MEMBER))

        val result = changeRole(GROUP_ID, "ana", Role.MEMBER)

        assertEquals(AppResult.Failure(DomainError.LastLeader), result)
        coVerify(exactly = 0) { repository.changeMemberRole(any(), any(), any()) }
    }

    @Test
    fun `a leader can be demoted when another leader remains`() = runTest {
        givenMembers(member("ana", Role.LEADER), member("ben", Role.LEADER))

        val result = changeRole(GROUP_ID, "ana", Role.MEMBER)

        assertEquals(AppResult.Success(Unit), result)
        coVerify { repository.changeMemberRole(GROUP_ID, "ana", Role.MEMBER) }
    }

    @Test
    fun `advisers are never converted to or from students`() = runTest {
        givenMembers(member("ana", Role.LEADER), member("dr-cruz", Role.ADVISER))

        assertEquals(
            AppResult.Failure(DomainError.PermissionDenied),
            changeRole(GROUP_ID, "dr-cruz", Role.LEADER),
        )
        assertEquals(
            AppResult.Failure(DomainError.PermissionDenied),
            changeRole(GROUP_ID, "ana", Role.ADVISER),
        )
    }

    @Test
    fun `setting the same role is a no-op`() = runTest {
        givenMembers(member("ana", Role.LEADER), member("ben", Role.MEMBER))

        assertEquals(AppResult.Success(Unit), changeRole(GROUP_ID, "ben", Role.MEMBER))
        coVerify(exactly = 0) { repository.changeMemberRole(any(), any(), any()) }
    }
}
