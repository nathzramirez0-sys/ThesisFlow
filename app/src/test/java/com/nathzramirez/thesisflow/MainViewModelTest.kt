package com.nathzramirez.thesisflow

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.User
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.NetworkMonitor
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val currentUser = MutableStateFlow<User?>(null)

    private val authRepository = mockk<AuthRepository> {
        every { authState } returns this@MainViewModelTest.authState
    }
    private val userRepository = mockk<UserRepository> {
        every { observeCurrentUser() } returns currentUser
    }

    private val network = mockk<NetworkMonitor> { every { isOnline } returns flowOf(true) }

    private fun viewModel() = MainViewModel(authRepository, userRepository, mockk<ChapterRepository>(), network, SavedStateHandle())

    private fun user(onboarded: Boolean) = User(
        uid = "ana",
        email = "ana@school.edu.ph",
        displayName = "Ana",
        photoUrl = null,
        course = "BSCS",
        school = "UPang",
        onboardingComplete = onboarded,
    )

    @Test
    fun `session follows sign-in, onboarding and sign-out`() = runTest {
        coEvery { userRepository.refreshCurrentUser() } returns AppResult.Success(user(onboarded = false))
        val viewModel = viewModel()

        viewModel.session.test {
            assertEquals(SessionState.SignedOut, awaitItem())

            authState.value = AuthState.SignedIn("ana")
            assertEquals(SessionState.Loading, awaitItem())

            currentUser.value = user(onboarded = false)
            assertEquals(SessionState.NeedsOnboarding, awaitItem())

            currentUser.value = user(onboarded = true)
            assertEquals(SessionState.Ready, awaitItem())

            authState.value = AuthState.SignedOut
            assertEquals(SessionState.SignedOut, awaitItem())
        }
    }

    @Test
    fun `a missing profile is fetched, and a failed fetch offers a retry`() = runTest {
        coEvery { userRepository.refreshCurrentUser() } returns AppResult.Failure(DomainError.Network)
        authState.value = AuthState.SignedIn("ana")
        val viewModel = viewModel()

        viewModel.session.test {
            assertEquals(SessionState.ProfileUnavailable, expectMostRecentItem())
        }
        coVerify(exactly = 1) { userRepository.refreshCurrentUser() }
    }

    @Test
    fun `only valid invite links are kept as pending`() {
        val viewModel = viewModel()

        viewModel.onLinkOpened("https://example.com/join/K7QM2P")
        assertNull(viewModel.pendingInviteCode.value)

        viewModel.onLinkOpened("https://${BuildConfig.INVITE_HOST}/join/k7qm2p")
        assertEquals("K7QM2P", viewModel.pendingInviteCode.value)

        viewModel.onInviteHandled()
        assertNull(viewModel.pendingInviteCode.value)
    }
}
