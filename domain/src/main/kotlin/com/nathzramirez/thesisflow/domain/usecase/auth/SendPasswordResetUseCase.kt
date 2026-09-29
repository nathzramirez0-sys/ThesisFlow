package com.nathzramirez.thesisflow.domain.usecase.auth

import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.Validators
import javax.inject.Inject

class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String): AppResult<Unit> {
        Validators.email(email)?.let { error ->
            return AppResult.Failure(DomainError.InvalidInput(mapOf(Field.EMAIL to error)))
        }
        return authRepository.sendPasswordReset(email.trim())
    }
}
