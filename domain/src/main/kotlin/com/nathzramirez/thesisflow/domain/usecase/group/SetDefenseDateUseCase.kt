package com.nathzramirez.thesisflow.domain.usecase.group

import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Sets or clears a defense date. Leader-only, like every other group detail.
 * A final defense can't come before the proposal defense, whichever is set first.
 */
class SetDefenseDateUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, kind: DefenseKind, at: Instant?): AppResult<Unit> {
        val group = groupRepository.observeGroup(groupId).first()
            ?: return AppResult.Failure(DomainError.NotFound)
        if (!group.myRole.canManageGroup) return AppResult.Failure(DomainError.PermissionDenied)

        if (at != null) {
            val proposal = if (kind == DefenseKind.PROPOSAL) at else group.proposalDefenseAt
            val final = if (kind == DefenseKind.FINAL) at else group.finalDefenseAt
            if (proposal != null && final != null && !final.isAfter(proposal)) {
                return AppResult.Failure(
                    DomainError.InvalidInput(mapOf(Field.DEFENSE_DATE to ValidationError.FINAL_BEFORE_PROPOSAL)),
                )
            }
        }
        return groupRepository.setDefenseDate(groupId, kind, at)
    }
}
