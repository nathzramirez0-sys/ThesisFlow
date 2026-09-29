package com.nathzramirez.thesisflow.domain.usecase.group

import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.Validators
import com.nathzramirez.thesisflow.domain.validation.fieldErrorsOf
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    /** Returns the new group's id. */
    suspend operator fun invoke(
        name: String,
        thesisTitle: String,
        course: String,
        school: String,
    ): AppResult<String> {
        val errors = fieldErrorsOf(
            Field.GROUP_NAME to Validators.groupName(name),
            Field.THESIS_TITLE to Validators.thesisTitle(thesisTitle),
            Field.COURSE to Validators.course(course),
            Field.SCHOOL to Validators.school(school),
        )
        if (errors.isNotEmpty()) return AppResult.Failure(DomainError.InvalidInput(errors))
        return groupRepository.createGroup(name.trim(), thesisTitle.trim(), course.trim(), school.trim())
    }
}
