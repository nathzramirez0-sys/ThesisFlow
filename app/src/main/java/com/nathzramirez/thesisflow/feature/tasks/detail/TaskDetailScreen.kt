package com.nathzramirez.thesisflow.feature.tasks.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.FormTextField
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.component.SegmentedControl
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.PersonName
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.UploadRules
import com.nathzramirez.thesisflow.feature.chapters.PendingSyncLabel
import com.nathzramirez.thesisflow.feature.chapters.detail.FileOpener
import com.nathzramirez.thesisflow.feature.files.AttachmentRow
import com.nathzramirez.thesisflow.feature.files.PendingUploadRow
import com.nathzramirez.thesisflow.feature.files.fileDateFormatter
import com.nathzramirez.thesisflow.feature.tasks.AssigneeStack
import com.nathzramirez.thesisflow.feature.tasks.PriorityPill
import com.nathzramirez.thesisflow.feature.tasks.TaskDueText
import com.nathzramirez.thesisflow.feature.tasks.taskStatusLabel
import com.nathzramirez.thesisflow.navigation.TaskDetailRoute
import com.nathzramirez.thesisflow.ui.messageRes
import com.nathzramirez.thesisflow.ui.validationMessage
import java.time.Instant

@Composable
fun TaskDetailScreen(
    route: TaskDetailRoute,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenChapter: (chapterId: String, number: Int) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel<TaskDetailViewModel, TaskDetailViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val now = remember { Instant.now() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFilePicked(uri.toString())
    }

    LaunchedEffect(state.isClosed) {
        if (state.isClosed) onBack()
    }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        viewModel.errorShown()
    }
    LaunchedEffect(state.fileToOpen) {
        val file = state.fileToOpen ?: return@LaunchedEffect
        viewModel.fileOpened()
        if (!FileOpener.open(context, file.path, file.mimeType)) {
            snackbarHostState.showSnackbar(context.getString(R.string.file_no_app))
        }
    }

    val task = state.task
    AuroraScaffold(
        topBar = {
            AuroraTopBar(title = "", onBack = onBack, actions = {
                if (state.canManage && task != null) ManageActions(onEdit = onEdit, onDelete = viewModel::askDelete)
            })
        },
        bottomBar = {
            if (task != null) {
                CommentComposer(
                    draft = state.commentDraft,
                    error = state.commentError?.let { validationMessage(Field.COMMENT, it) },
                    sending = state.isSendingComment,
                    onChange = viewModel::onCommentChange,
                    onSend = viewModel::sendComment,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (task == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@AuroraScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(task) }
            item {
                StatusSection(
                    task = task,
                    enabled = state.canChangeStatus,
                    onChange = viewModel::changeStatus,
                )
            }
            item {
                InfoCard(
                    task = task,
                    now = now,
                    state = state,
                    onOpenChapter = { linked -> onOpenChapter(linked.chapter.id, linked.number) },
                )
            }
            if (task.description.isNotBlank()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        HudLabel(stringResource(R.string.task_detail_details))
                        Text(task.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.attachments_title), modifier = Modifier.padding(top = 8.dp)) }
            if (state.canAttach) {
                item {
                    GhostButton(
                        text = stringResource(R.string.attachments_add),
                        onClick = { picker.launch(UploadRules.pickerTypes.toTypedArray()) },
                        icon = ImageVector.vectorResource(R.drawable.ic_upload),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.uploads, key = { "upload-${it.fileId}" }) { upload ->
                PendingUploadRow(
                    upload = upload,
                    onRetry = { viewModel.retryUpload(upload.fileId) },
                    onDiscard = { viewModel.discardUpload(upload.fileId) },
                )
            }
            items(state.attachments, key = { "file-${it.id}" }) { file ->
                AttachmentRow(
                    file = file,
                    uploaderName = state.members[file.uploadedBy]?.displayName,
                    isOpening = state.openingFileId == file.id,
                    onOpen = { viewModel.openFile(file) },
                )
            }
            if (state.attachments.isEmpty() && state.uploads.isEmpty()) {
                item { QuietText(stringResource(R.string.attachments_none)) }
            }

            item {
                SectionHeader(
                    stringResource(R.string.comments_title, state.comments.size),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.comments.isEmpty()) item { QuietText(stringResource(R.string.comments_none)) }
            items(state.comments, key = { "comment-${it.id}" }) { comment ->
                CommentRow(
                    comment = comment,
                    isMine = comment.authorId == state.myUid,
                    photoUrl = state.members[comment.authorId]?.photoUrl,
                    canDelete = state.canDelete(comment),
                    onDelete = { viewModel.deleteComment(comment) },
                )
            }
        }
    }

    if (state.confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.task_delete_title, task.title)) },
            text = { Text(stringResource(R.string.task_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::delete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ManageActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.task_edit)) }
    Box {
        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun Header(task: Task) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PriorityPill(task.priority)
        Text(task.title, style = MaterialTheme.typography.headlineMedium)
        if (task.hasPendingWrites) PendingSyncLabel()
    }
}

/** The three columns as one control; locked for people who can't move this task. */
@Composable
private fun StatusSection(task: Task, enabled: Boolean, onChange: (TaskStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HudLabel(stringResource(R.string.task_detail_status))
        SegmentedControl(
            options = TaskStatus.entries.map { taskStatusLabel(it) },
            selectedIndex = task.status.ordinal,
            onSelect = { onChange(TaskStatus.entries[it]) },
            enabled = enabled,
        )
        if (!enabled) QuietText(stringResource(R.string.task_status_locked))
    }
}

@Composable
private fun InfoCard(task: Task, now: Instant, state: TaskDetailUiState, onOpenChapter: (LinkedChapter) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        InfoRow(label = stringResource(R.string.task_detail_due)) {
            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            if (task.dueAt == null) QuietText(stringResource(R.string.task_no_due)) else TaskDueText(task, now)
        }
        val linked = state.chapter
        InfoRow(
            label = stringResource(R.string.task_detail_chapter),
            onClick = linked?.let { { onOpenChapter(it) } },
        ) {
            if (linked == null) {
                QuietText(stringResource(R.string.task_no_chapter))
            } else {
                Text(
                    stringResource(R.string.chapter_number, linked.number) + " · " + linked.chapter.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = AuroraTheme.colors.gradientEnd,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AuroraTheme.colors.gradientEnd)
            }
        }
        InfoRow(label = stringResource(R.string.task_detail_assignees)) {
            val assignees = task.assigneeIds.mapNotNull(state.members::get)
            AssigneeStack(assignees)
            if (assignees.isNotEmpty()) {
                Text(
                    assignees.joinToString { PersonName.firstName(it.displayName) },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudLabel(label, modifier = Modifier.padding(end = 6.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun CommentRow(
    comment: TaskComment,
    isMine: Boolean,
    photoUrl: String?,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(comment.authorName, photoUrl, size = 36.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(if (isMine) aurora.glassFillStrong else aurora.glassFill)
                .border(1.dp, aurora.glassBorder, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMine) stringResource(R.string.comment_you) else comment.authorName.ifBlank { stringResource(R.string.member_unnamed) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    comment.createdAt?.let(fileDateFormatter::format).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            if (comment.hasPendingWrites) PendingSyncLabel()
            if (canDelete) {
                TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp), modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.comment_delete), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Pinned above the keyboard, like a chat box. */
@Composable
private fun CommentComposer(
    draft: String,
    error: String?,
    sending: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    // Solid, with a hairline on top, so comments scrolling underneath never show through.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .drawBehind {
                drawLine(outline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormTextField(
            value = draft,
            onValueChange = onChange,
            label = stringResource(R.string.comment_hint),
            error = error,
            enabled = !sending,
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (draft.isNotBlank()) AuroraTheme.colors.buttonGradient else AuroraTheme.colors.glassBorder),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.comment_send), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun QuietText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
