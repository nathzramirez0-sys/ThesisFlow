package com.nathzramirez.thesisflow.feature.groups.createjoin

import com.nathzramirez.thesisflow.MainDispatcherRule
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.JoinGroupUseCase
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class JoinGroupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groupRepository = mockk<GroupRepository>()
    private val viewModel = JoinGroupViewModel(JoinGroupUseCase(groupRepository))

    @Test
    fun `a pasted code is normalized before it is sent`() = runTest {
        coEvery { groupRepository.joinGroup("K7QM2P") } returns AppResult.Success("group-1")

        viewModel.onCodeChange("k7qm-2p")
        viewModel.join()

        coVerify { groupRepository.joinGroup("K7QM2P") }
        assertEquals("group-1", viewModel.uiState.value.joinedGroupId)
    }

    @Test
    fun `a malformed code is caught locally`() = runTest {
        viewModel.onCodeChange("K7QM0")
        viewModel.join()

        assertEquals(ValidationError.INVALID_INVITE_CODE, viewModel.uiState.value.codeError)
        coVerify(exactly = 0) { groupRepository.joinGroup(any()) }
    }

    @Test
    fun `server-side invite errors reach the user`() = runTest {
        coEvery { groupRepository.joinGroup(any()) } returns AppResult.Failure(DomainError.InviteExpired)

        viewModel.onCodeChange("K7QM2P")
        viewModel.join()

        assertEquals(DomainError.InviteExpired, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.joinedGroupId)
    }

    @Test
    fun `a link prefills the code but never overwrites typing`() {
        viewModel.prefill("K7QM2P")
        assertEquals("K7QM2P", viewModel.uiState.value.code)

        viewModel.onCodeChange("ABCDEF")
        viewModel.prefill("K7QM2P")
        assertEquals("ABCDEF", viewModel.uiState.value.code)
    }
}
