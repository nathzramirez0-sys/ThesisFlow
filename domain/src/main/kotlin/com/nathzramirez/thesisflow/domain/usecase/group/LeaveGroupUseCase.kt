package com.nathzramirez.thesisflow.domain.usecase.group

import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The only leader cannot leave: they must promote someone first, or delete the
 * group if they are its last member. Otherwise the group would have no leader.
 */
class LeaveGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String): AppResult<Unit> {
        val group = groupRepository.observeGroup(groupId).first()
            ?: return AppResult.Failure(DomainError.NotFound)

        if (group.myRole == Role.LEADER) {
            val leaderCount = groupRepository.observeMembers(groupId).first()
                .count { it.role == Role.LEADER }
            if (leaderCount <= 1) return AppResult.Failure(DomainError.LastLeader)
        }
        return groupRepository.leaveGroup(groupId)
    }
}
