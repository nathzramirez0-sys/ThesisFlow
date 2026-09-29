package com.nathzramirez.thesisflow.feature.groups.overview

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.BuildConfig
import com.nathzramirez.thesisflow.InviteLinks
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.CodeTiles
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowPill
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.ProgressRing
import com.nathzramirez.thesisflow.designsystem.component.RoleBadge
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.feature.activity.ActivityEntry
import com.nathzramirez.thesisflow.feature.activity.ActivityLink
import com.nathzramirez.thesisflow.feature.activity.ActivityRow
import com.nathzramirez.thesisflow.navigation.GroupOverviewRoute
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Invite
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.ThesisProgress
import com.nathzramirez.thesisflow.ui.messageRes
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Actions that need a "are you sure?" dialog first. */
private sealed interface Confirmation {
    data class RemoveMember(val member: Member) : Confirmation
    data class ReplaceInvite(val role: Role) : Confirmation
    data object Leave : Confirmation
    data object Delete : Confirmation
}

@Composable
fun GroupOverviewScreen(
    route: GroupOverviewRoute,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenActivityLink: (ActivityLink) -> Unit,
    viewModel: GroupOverviewViewModel = hiltViewModel<GroupOverviewViewModel, GroupOverviewViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isClosed) {
        if (state.isClosed) onBack()
    }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        viewModel.errorShown()
    }

    val group = state.group
    AuroraScaffold(
        topBar = {
            AuroraTopBar(title = "", onBack = onBack, actions = {
                if (group != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_leave)) },
                                onClick = {
                                    menuOpen = false
                                    confirmation = Confirmation.Leave
                                },
                            )
                            if (group.myRole.canManageGroup) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuOpen = false
                                        confirmation = Confirmation.Delete
                                    },
                                )
                            }
                        }
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (group == null) {
            FullScreenLoading(Modifier.padding(padding), message = stringResource(R.string.group_loading))
            return@AuroraScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Hero(group) }
            item { ChaptersCard(progress = state.progress, openFeedback = state.openFeedback, onClick = onOpenChapters) }
            item { TasksCard(summary = state.tasks, onClick = onOpenTasks) }
            item {
                RecentActivityCard(
                    entries = state.recentActivity,
                    onOpen = onOpenActivityLink,
                    onSeeAll = onOpenActivity,
                )
            }

            if (group.myRole.canInvite) {
                item {
                    InvitesCard(
                        group = group,
                        invites = state.invites,
                        creatingFor = state.creatingInviteFor,
                        enabled = !state.isBusy,
                        onCreate = { role ->
                            val existing = state.invites[role]
                            if (existing != null && !existing.isExpired(Instant.now())) {
                                confirmation = Confirmation.ReplaceInvite(role)
                            } else {
                                viewModel.createInvite(role)
                            }
                        },
                        onCopied = {
                            // Android 13+ shows its own "copied" confirmation.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                snackbarHostState.showSnackbar(context.getString(R.string.invite_copied))
                            }
                        },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.members_title, state.members.size)) }
            item {
                GlassCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                    state.members.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        MemberRow(
                            member = member,
                            isMe = member.uid == state.myUid,
                            canManage = group.myRole.canManageGroup && member.uid != state.myUid,
                            enabled = !state.isBusy,
                            onChangeRole = { role -> viewModel.changeRole(member, role) },
                            onRemove = { confirmation = Confirmation.RemoveMember(member) },
                        )
                    }
                }
            }
        }
    }

    confirmation?.let { current ->
        ConfirmationDialog(
            confirmation = current,
            groupName = group?.name.orEmpty(),
            onConfirm = {
                confirmation = null
                when (current) {
                    is Confirmation.RemoveMember -> viewModel.removeMember(current.member)
                    is Confirmation.ReplaceInvite -> viewModel.createInvite(current.role)
                    Confirmation.Leave -> viewModel.leave()
                    Confirmation.Delete -> viewModel.delete()
                }
            },
            onDismiss = { confirmation = null },
        )
    }
}

@Composable
private fun Hero(group: Group) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        HudLabel(listOf(group.course, group.school).filter { it.isNotBlank() }.joinToString(" · "))
        Text(group.name, style = MaterialTheme.typography.headlineLarge)
        Text(
            group.thesisTitle.ifBlank { stringResource(R.string.groups_no_title) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleBadge(group.myRole)
            Text(
                pluralStringResource(R.plurals.groups_member_count, group.memberCount, group.memberCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The way into the chapter tracker, with overall progress at a glance. */
@Composable
private fun ChaptersCard(progress: ThesisProgress, openFeedback: Int, onClick: () -> Unit) {
    GlassCard(onClick = onClick, strong = true, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(percent = progress.percent, size = 96.dp, strokeWidth = 8.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HudLabel(stringResource(R.string.chapters_overall))
                Text(
                    stringResource(R.string.chapters_progress, progress.approvedCount, progress.chapterCount),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (openFeedback > 0) {
                    GlowPill(
                        pluralStringResource(R.plurals.feedback_open_count, openFeedback, openFeedback),
                        AuroraTheme.colors.statusRevisions,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.chapters_open),
                        style = MaterialTheme.typography.labelLarge,
                        color = AuroraTheme.colors.gradientEnd,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AuroraTheme.colors.gradientEnd,
                    )
                }
            }
        }
    }
}

/** The latest few things that happened, each linking to its chapter or task. */
@Composable
private fun RecentActivityCard(entries: List<ActivityEntry>, onOpen: (ActivityLink) -> Unit, onSeeAll: () -> Unit) {
    val now = remember { Instant.now() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.activity_title)) {
            if (entries.isNotEmpty()) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.activity_see_all)) }
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.activity_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                ActivityRow(entry = entry, now = now, onOpen = onOpen)
            }
        }
    }
}

/** Open work at a glance: how much is left, what's late, and what's yours. */
@Composable
private fun TasksCard(summary: TaskSummary, onClick: () -> Unit) {
    val aurora = AuroraTheme.colors
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudLabel(stringResource(R.string.tasks_title))
                Text(
                    pluralStringResource(R.plurals.tasks_open_count, summary.open, summary.open),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.overdue > 0) {
                        GlowPill(
                            pluralStringResource(R.plurals.tasks_overdue_count, summary.overdue, summary.overdue),
                            aurora.statusRevisions,
                        )
                    }
                    if (summary.mine > 0) {
                        GlowPill(
                            pluralStringResource(R.plurals.tasks_mine_count, summary.mine, summary.mine),
                            aurora.gradientEnd,
                        )
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tasks_open_board), tint = aurora.gradientEnd)
        }
    }
}

@Composable
private fun InvitesCard(
    group: Group,
    invites: Map<Role, Invite>,
    creatingFor: Role?,
    enabled: Boolean,
    onCreate: (Role) -> Unit,
    onCopied: suspend () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.invites_title))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InviteRow(
                    label = stringResource(R.string.invite_member_code),
                    groupName = group.name,
                    invite = invites[Role.MEMBER],
                    isCreating = creatingFor == Role.MEMBER,
                    enabled = enabled,
                    onCreate = { onCreate(Role.MEMBER) },
                    onCopied = onCopied,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InviteRow(
                    label = stringResource(R.string.invite_adviser_code),
                    groupName = group.name,
                    invite = invites[Role.ADVISER],
                    isCreating = creatingFor == Role.ADVISER,
                    enabled = enabled,
                    onCreate = { onCreate(Role.ADVISER) },
                    onCopied = onCopied,
                )
            }
        }
    }
}

@Composable
private fun InviteRow(
    label: String,
    groupName: String,
    invite: Invite?,
    isCreating: Boolean,
    enabled: Boolean,
    onCreate: () -> Unit,
    onCopied: suspend () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val active = invite?.takeUnless { it.isExpired(Instant.now()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudLabel(label, modifier = Modifier.weight(1f))
            if (isCreating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onCreate, enabled = enabled) {
                    Text(stringResource(if (active != null) R.string.invite_new else R.string.invite_create))
                }
            }
        }
        if (active != null) {
            CodeTiles(active.code)
            Text(
                stringResource(R.string.invite_expires, expiryFormatter.format(active.expiresAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = stringResource(R.string.invite_share),
                    onClick = { shareInvite(context, groupName, active.code) },
                    icon = Icons.Filled.Share,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = stringResource(R.string.invite_copy),
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, active.code)))
                            onCopied()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                stringResource(if (invite != null) R.string.invite_expired else R.string.invite_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: Member,
    isMe: Boolean,
    canManage: Boolean,
    enabled: Boolean,
    onChangeRole: (Role) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = member.displayName.ifBlank { stringResource(R.string.member_unnamed) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(name = name, photoUrl = member.photoUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (isMe) stringResource(R.string.member_you, name) else name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RoleBadge(member.role)
        }
        if (canManage) {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Advisers keep their role; students switch between leader and member.
                    when (member.role) {
                        Role.MEMBER -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.member_make_leader)) },
                            onClick = {
                                menuOpen = false
                                onChangeRole(Role.LEADER)
                            },
                        )
                        Role.LEADER -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.member_make_member)) },
                            onClick = {
                                menuOpen = false
                                onChangeRole(Role.MEMBER)
                            },
                        )
                        Role.ADVISER -> Unit
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.member_remove), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    confirmation: Confirmation,
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, body, action) = when (confirmation) {
        is Confirmation.RemoveMember -> Triple(
            stringResource(R.string.member_remove_title, confirmation.member.displayName),
            stringResource(R.string.member_remove_body),
            stringResource(R.string.member_remove),
        )
        is Confirmation.ReplaceInvite -> Triple(
            stringResource(R.string.invite_replace_title),
            stringResource(R.string.invite_replace_body),
            stringResource(R.string.invite_new),
        )
        Confirmation.Leave -> Triple(
            stringResource(R.string.group_leave_title, groupName),
            stringResource(R.string.group_leave_body),
            stringResource(R.string.group_leave),
        )
        Confirmation.Delete -> Triple(
            stringResource(R.string.group_delete_title, groupName),
            stringResource(R.string.group_delete_body),
            stringResource(R.string.group_delete),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val expiryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

private fun shareInvite(context: Context, groupName: String, code: String) {
    val link = InviteLinks.build(BuildConfig.INVITE_HOST, code)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_share_text, groupName, link, code))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.invite_share_chooser)))
}
