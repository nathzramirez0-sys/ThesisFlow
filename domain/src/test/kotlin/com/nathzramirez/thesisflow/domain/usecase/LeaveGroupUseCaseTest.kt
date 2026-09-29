package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.LeaveGroupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaveGroupUseCaseTest {

    private val repository = mockk<GroupRepository> {
        coEvery { leaveGroup(any()) } returns AppResult.Success(Unit)
    }
    private val leaveGroup = LeaveGroupUseCase(repository)

    @Test
    fun `a member can always leave`() = runTest {
        every { repository.observeGroup(GROUP_ID) } returns flowOf(group(Role.MEMBER, memberCount = 3))

        assertEquals(AppResult.Success(Unit), leaveGroup(GROUP_ID))
        coVerify { repository.leaveGroup(GROUP_ID) }
    }

    @Test
    fun `the only leader has to hand over first`() = runTest {
        every { repository.observeGroup(GROUP_ID) } returns flowOf(group(Role.LEADER, memberCount = 2))
        every { repository.observeMembers(GROUP_ID) } returns
            flowOf(listOf(member("ana", Role.LEADER), member("ben", Role.MEMBER)))

        assertEquals(AppResult.Failure(DomainError.LastLeader), leaveGroup(GROUP_ID))
        coVerify(exactly = 0) { repository.leaveGroup(any()) }
    }

    @Test
    fun `a leader can leave when a co-leader stays`() = runTest {
        every { repository.observeGroup(GROUP_ID) } returns flowOf(group(Role.LEADER, memberCount = 2))
        every { repository.observeMembers(GROUP_ID) } returns
            flowOf(listOf(member("ana", Role.LEADER), member("ben", Role.LEADER)))

        assertEquals(AppResult.Success(Unit), leaveGroup(GROUP_ID))
    }

    @Test
    fun `leaving a group that is no longer cached reports not found`() = runTest {
        every { repository.observeGroup(GROUP_ID) } returns flowOf(null)

        assertEquals(AppResult.Failure(DomainError.NotFound), leaveGroup(GROUP_ID))
    }
}
