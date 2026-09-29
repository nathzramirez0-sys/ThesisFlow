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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.BuildConfig
import com.nathzramirez.thesisflow.InviteLinks
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.RoleBadge
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Invite
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupOverviewScreen(
    onBack: () -> Unit,
    viewModel: GroupOverviewViewModel = hiltViewModel(),
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
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
                                        text = {
                                            Text(
                                                stringResource(R.string.group_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            confirmation = Confirmation.Delete
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (group == null) {
            FullScreenLoading(Modifier.padding(padding), message = stringResource(R.string.group_loading))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GroupHeader(group) }

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

            item {
                Text(
                    stringResource(R.string.members_title, state.members.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.members, key = { it.uid }) { member ->
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
private fun GroupHeader(group: Group) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                group.thesisTitle.ifBlank { stringResource(R.string.groups_no_title) },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                listOf(group.course, group.school).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    when (group.myRole) {
                        Role.LEADER -> R.string.group_you_are_leader
                        Role.MEMBER -> R.string.group_you_are_member
                        Role.ADVISER -> R.string.group_you_are_adviser
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.invites_title), style = MaterialTheme.typography.titleMedium)
            InviteRow(
                label = stringResource(R.string.invite_member_code),
                groupName = group.name,
                invite = invites[Role.MEMBER],
                isCreating = creatingFor == Role.MEMBER,
                enabled = enabled,
                onCreate = { onCreate(Role.MEMBER) },
                onCopied = onCopied,
            )
            HorizontalDivider()
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (active != null) {
            Text(
                active.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                letterSpacing = 4.sp,
            )
            Text(
                stringResource(R.string.invite_expires, expiryFormatter.format(active.expiresAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(if (invite != null) R.string.invite_expired else R.string.invite_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active != null) {
                Button(onClick = { shareInvite(context, groupName, active.code) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.invite_share), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, active.code)))
                        onCopied()
                    }
                }) {
                    Text(stringResource(R.string.invite_copy))
                }
            }
            if (isCreating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onCreate, enabled = enabled) {
                    Text(stringResource(if (active != null) R.string.invite_new else R.string.invite_create))
                }
            }
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

    ListItem(
        headlineContent = { Text(if (isMe) stringResource(R.string.member_you, name) else name) },
        leadingContent = { Avatar(name = name, photoUrl = member.photoUrl) },
        supportingContent = { RoleBadge(member.role) },
        trailingContent = if (canManage) {
            {
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
        } else {
            null
        },
    )
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
