package com.nathzramirez.thesisflow.domain.usecase.profile

import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.Validators
import com.nathzramirez.thesisflow.domain.validation.fieldErrorsOf
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(displayName: String, course: String, school: String): AppResult<Unit> {
        val errors = fieldErrorsOf(
            Field.DISPLAY_NAME to Validators.displayName(displayName),
            Field.COURSE to Validators.course(course),
            Field.SCHOOL to Validators.school(school),
        )
        if (errors.isNotEmpty()) return AppResult.Failure(DomainError.InvalidInput(errors))
        return userRepository.updateProfile(displayName.trim(), course.trim(), school.trim())
    }
}
