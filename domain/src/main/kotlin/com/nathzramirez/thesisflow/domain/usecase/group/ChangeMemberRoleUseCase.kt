package com.nathzramirez.thesisflow.domain.usecase.group

import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Promotes or demotes a student between leader and member.
 *
 * Advisers join through an adviser invite and are never converted to or from
 * students. A group must always keep at least one leader; the Firestore rules
 * enforce the same thing, this just explains it before the server says no.
 */
class ChangeMemberRoleUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, memberUid: String, newRole: Role): AppResult<Unit> {
        if (newRole == Role.ADVISER) return AppResult.Failure(DomainError.PermissionDenied)

        val members = groupRepository.observeMembers(groupId).first()
        val target = members.firstOrNull { it.uid == memberUid }
            ?: return AppResult.Failure(DomainError.NotFound)

        if (target.role == Role.ADVISER) return AppResult.Failure(DomainError.PermissionDenied)
        if (target.role == newRole) return AppResult.Success(Unit)

        val leaderCount = members.count { it.role == Role.LEADER }
        if (target.role == Role.LEADER && leaderCount <= 1) {
            return AppResult.Failure(DomainError.LastLeader)
        }
        return groupRepository.changeMemberRole(groupId, memberUid, newRole)
    }
}
