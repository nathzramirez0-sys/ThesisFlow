package com.nathzramirez.thesisflow.domain.usecase.auth

import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.Validators
import com.nathzramirez.thesisflow.domain.validation.fieldErrorsOf
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> {
        val errors = fieldErrorsOf(
            Field.EMAIL to Validators.email(email),
            Field.PASSWORD to Validators.existingPassword(password),
        )
        if (errors.isNotEmpty()) return AppResult.Failure(DomainError.InvalidInput(errors))
        return authRepository.signInWithEmail(email.trim(), password)
    }
}
