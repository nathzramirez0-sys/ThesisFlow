package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.auth.SignUpUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignUpUseCaseTest {

    private val authRepository = mockk<AuthRepository>()
    private val signUp = SignUpUseCase(authRepository)

    @Test
    fun `invalid input never reaches the repository`() = runTest {
        val result = signUp(email = "juan@", password = "short", confirmPassword = "other")

        val expected = DomainError.InvalidInput(
            mapOf(
                Field.EMAIL to ValidationError.INVALID_EMAIL,
                Field.PASSWORD to ValidationError.PASSWORD_TOO_SHORT,
                Field.CONFIRM_PASSWORD to ValidationError.PASSWORDS_DO_NOT_MATCH,
            ),
        )
        assertEquals(AppResult.Failure(expected), result)
        coVerify(exactly = 0) { authRepository.signUpWithEmail(any(), any()) }
    }

    @Test
    fun `valid input is trimmed and passed on`() = runTest {
        coEvery { authRepository.signUpWithEmail(any(), any()) } returns AppResult.Success(Unit)

        val result = signUp(" juan@up.edu.ph ", "thesis2026", "thesis2026")

        assertEquals(AppResult.Success(Unit), result)
        coVerify { authRepository.signUpWithEmail("juan@up.edu.ph", "thesis2026") }
    }

    @Test
    fun `repository failures are returned unchanged`() = runTest {
        coEvery { authRepository.signUpWithEmail(any(), any()) } returns
            AppResult.Failure(DomainError.EmailAlreadyInUse)

        val result = signUp("juan@up.edu.ph", "thesis2026", "thesis2026")

        assertEquals(AppResult.Failure(DomainError.EmailAlreadyInUse), result)
    }
}
