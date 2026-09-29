package com.nathzramirez.thesisflow.feature.auth

import com.nathzramirez.thesisflow.MainDispatcherRule
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.auth.SendPasswordResetUseCase
import com.nathzramirez.thesisflow.domain.usecase.auth.SignInUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Uses the real use cases with a mocked repository, so validation is exercised end to end. */
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>()
    private val viewModel = LoginViewModel(
        signIn = SignInUseCase(authRepository),
        sendPasswordReset = SendPasswordResetUseCase(authRepository),
        authRepository = authRepository,
    )

    @Test
    fun `invalid fields show errors and never call Firebase`() = runTest {
        viewModel.onEmailChange("ana@")
        viewModel.signIn()

        val state = viewModel.uiState.value
        assertEquals(ValidationError.INVALID_EMAIL, state.fieldErrors[Field.EMAIL])
        assertEquals(ValidationError.REQUIRED, state.fieldErrors[Field.PASSWORD])
        assertFalse(state.isSubmitting)
        coVerify(exactly = 0) { authRepository.signInWithEmail(any(), any()) }
    }

    @Test
    fun `editing a field clears only that field's error`() = runTest {
        viewModel.signIn()
        viewModel.onEmailChange("ana@school.edu.ph")

        val errors = viewModel.uiState.value.fieldErrors
        assertNull(errors[Field.EMAIL])
        assertEquals(ValidationError.REQUIRED, errors[Field.PASSWORD])
    }

    @Test
    fun `wrong credentials surface as a one-off message`() = runTest {
        coEvery { authRepository.signInWithEmail(any(), any()) } returns
            AppResult.Failure(DomainError.InvalidCredentials)

        viewModel.onEmailChange("ana@school.edu.ph")
        viewModel.onPasswordChange("thesis2026")
        viewModel.signIn()

        assertEquals(DomainError.InvalidCredentials, viewModel.uiState.value.error)
        viewModel.messageShown()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `cancelling the Google sheet shows nothing`() {
        viewModel.onGoogleSignInFailed(DomainError.SignInCancelled)
        assertNull(viewModel.uiState.value.error)

        viewModel.onGoogleSignInFailed(DomainError.NoGoogleAccount)
        assertEquals(DomainError.NoGoogleAccount, viewModel.uiState.value.error)
    }

    @Test
    fun `password reset starts from the typed email and confirms when sent`() = runTest {
        coEvery { authRepository.sendPasswordReset(any()) } returns AppResult.Success(Unit)

        viewModel.onEmailChange("ana@school.edu.ph")
        viewModel.openPasswordReset()
        assertEquals("ana@school.edu.ph", viewModel.uiState.value.passwordReset?.email)

        viewModel.sendResetLink()

        assertNull(viewModel.uiState.value.passwordReset)
        assertEquals("ana@school.edu.ph", viewModel.uiState.value.resetLinkSentTo)
    }

    @Test
    fun `an invalid reset email keeps the dialog open with an error`() = runTest {
        viewModel.openPasswordReset()
        viewModel.onResetEmailChange("not-an-email")
        viewModel.sendResetLink()

        assertEquals(ValidationError.INVALID_EMAIL, viewModel.uiState.value.passwordReset?.error)
        coVerify(exactly = 0) { authRepository.sendPasswordReset(any()) }
    }
}
