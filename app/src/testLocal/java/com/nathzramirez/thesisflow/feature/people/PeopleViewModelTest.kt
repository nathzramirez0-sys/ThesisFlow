package com.nathzramirez.thesisflow.feature.people

import app.cash.turbine.test
import com.nathzramirez.thesisflow.MainDispatcherRule
import com.nathzramirez.thesisflow.domain.model.Person
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.PeopleRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PeopleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carla = Person("carla", "Carla Mendoza", listOf(Person.Membership("Team SmartBin", Role.LEADER)))
    private val people = MutableStateFlow(listOf(carla))
    private val repository = mockk<PeopleRepository> {
        every { observePeople() } returns people
        coEvery { switchTo(any()) } returns AppResult.Success(Unit)
        coEvery { addPerson() } returns AppResult.Success(Unit)
        coEvery { addSampleGroup() } returns AppResult.Success(Unit)
    }

    @Test
    fun `the list shows everyone on the phone`() = runTest {
        PeopleViewModel(repository).uiState.test {
            assertEquals(listOf(carla), expectMostRecentItem().people)
        }
    }

    @Test
    fun `picking someone switches to them`() = runTest {
        val viewModel = PeopleViewModel(repository)
        viewModel.uiState.test {
            viewModel.choose("carla")
            expectMostRecentItem()
        }
        coVerify { repository.switchTo("carla") }
    }

    @Test
    fun `a failure is shown once`() = runTest {
        coEvery { repository.addSampleGroup() } returns AppResult.Failure(DomainError.Unknown("disk full"))
        val viewModel = PeopleViewModel(repository)
        viewModel.uiState.test {
            viewModel.addSampleGroup()
            assertEquals(DomainError.Unknown("disk full"), expectMostRecentItem().error)
            viewModel.errorShown()
            assertNull(expectMostRecentItem().error)
        }
    }

    @Test
    fun `a second tap while the first is running does nothing`() = runTest {
        val gate = CompletableDeferred<AppResult<Unit>>()
        coEvery { repository.addPerson() } coAnswers { gate.await() }
        val viewModel = PeopleViewModel(repository)
        viewModel.uiState.test {
            viewModel.addPerson()
            viewModel.addPerson()
            assertTrue(expectMostRecentItem().isBusy)
            gate.complete(AppResult.Success(Unit))
            assertFalse(expectMostRecentItem().isBusy)
        }
        coVerify(exactly = 1) { repository.addPerson() }
    }
}
