package com.nathzramirez.thesisflow.feature.groups.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Invite
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.ThesisProgress
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.ChangeMemberRoleUseCase
import com.nathzramirez.thesisflow.domain.usecase.group.LeaveGroupUseCase
import com.nathzramirez.thesisflow.navigation.GroupOverviewRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupOverviewUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val invites: Map<Role, Invite> = emptyMap(),
    val progress: ThesisProgress = ThesisProgress(0, 0, 0),
    val myUid: String? = null,
    val isBusy: Boolean = false,
    /** The invite role whose code is being generated, to show a spinner on that row. */
    val creatingInviteFor: Role? = null,
    val error: DomainError? = null,
    /** True once the user left, deleted the group, or was removed: the screen closes. */
    val isClosed: Boolean = false,
)

/** Transient state from user actions, combined with the data flows below. */
private data class ActionState(
    val isBusy: Boolean = false,
    val creatingInviteFor: Role? = null,
    val error: DomainError? = null,
    val finished: Boolean = false,
)

/**
 * Receives its route through assisted injection instead of reading SavedStateHandle,
 * so tests can pass a plain [GroupOverviewRoute].
 */
@HiltViewModel(assistedFactory = GroupOverviewViewModel.Factory::class)
class GroupOverviewViewModel @AssistedInject constructor(
    @Assisted private val route: GroupOverviewRoute,
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    chapterRepository: ChapterRepository,
    private val changeMemberRole: ChangeMemberRoleUseCase,
    private val leaveGroup: LeaveGroupUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: GroupOverviewRoute): GroupOverviewViewModel
    }

    private val groupId = route.groupId
    private val actions = MutableStateFlow(ActionState())

    /** Distinguishes "not synced yet" (just joined) from "gone" (removed or deleted). */
    private var hasSeenGroup = false

    private val group = groupRepository.observeGroup(groupId)

    /** Invites are leader-only, so only leaders open that listener. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val invites = group
        .map { it?.myRole?.canInvite == true }
        .distinctUntilChanged()
        .flatMapLatest { canInvite -> if (canInvite) groupRepository.observeInvites(groupId) else flowOf(emptyList()) }

    private val myUid = authRepository.authState.map { (it as? AuthState.SignedIn)?.uid }

    private val progress = chapterRepository.observeChapters(groupId).map(ThesisProgress::of)

    val uiState: StateFlow<GroupOverviewUiState> = combine(
        group,
        groupRepository.observeMembers(groupId),
        invites,
        myUid,
        actions,
    ) { group, members, invites, uid, action ->
        if (group != null) hasSeenGroup = true
        GroupOverviewUiState(
            isLoading = group == null && !hasSeenGroup,
            group = group,
            members = members,
            invites = invites.associateBy { it.role },
            myUid = uid,
            isBusy = action.isBusy,
            creatingInviteFor = action.creatingInviteFor,
            error = action.error,
            isClosed = action.finished || (group == null && hasSeenGroup),
        )
    }.combine(progress) { state, progress -> state.copy(progress = progress) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupOverviewUiState())

    fun createInvite(role: Role) = runAction(inviteRole = role) { groupRepository.createInvite(groupId, role) }

    fun changeRole(member: Member, role: Role) = runAction { changeMemberRole(groupId, member.uid, role) }

    fun removeMember(member: Member) = runAction { groupRepository.removeMember(groupId, member.uid) }

    fun leave() = runAction(closeOnSuccess = true) { leaveGroup(groupId) }

    fun delete() = runAction(closeOnSuccess = true) { groupRepository.deleteGroup(groupId) }

    fun errorShown() = actions.update { it.copy(error = null) }

    private fun runAction(
        inviteRole: Role? = null,
        closeOnSuccess: Boolean = false,
        action: suspend () -> AppResult<*>,
    ) {
        if (actions.value.isBusy) return
        actions.update { it.copy(isBusy = true, creatingInviteFor = inviteRole) }
        viewModelScope.launch {
            val result = action()
            actions.update {
                it.copy(
                    isBusy = false,
                    creatingInviteFor = null,
                    error = (result as? AppResult.Failure)?.error,
                    finished = it.finished || (closeOnSuccess && result is AppResult.Success),
                )
            }
        }
    }
}
