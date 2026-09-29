package com.nathzramirez.thesisflow.domain.usecase.group

import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.InviteCodeFormat
import com.nathzramirez.thesisflow.domain.validation.Validators
import javax.inject.Inject

class JoinGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    /** Returns the joined group's id. */
    suspend operator fun invoke(rawCode: String): AppResult<String> {
        val code = InviteCodeFormat.normalize(rawCode)
        Validators.inviteCode(code)?.let { error ->
            return AppResult.Failure(DomainError.InvalidInput(mapOf(Field.INVITE_CODE to error)))
        }
        return groupRepository.joinGroup(code)
    }
}
