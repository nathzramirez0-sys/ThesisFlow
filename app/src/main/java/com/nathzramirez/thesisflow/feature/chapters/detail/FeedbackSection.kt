package com.nathzramirez.thesisflow.feature.chapters.detail

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.FormTextField
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.GradientProgressBar
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.RoleBadge
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.component.SegmentedControl
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.UploadState
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.feature.chapters.PendingSyncLabel
import com.nathzramirez.thesisflow.feature.tasks.ChoiceChip
import com.nathzramirez.thesisflow.ui.messageRes
import com.nathzramirez.thesisflow.ui.relativeTimeText
import com.nathzramirez.thesisflow.ui.validationMessage
import java.time.Instant

/** What the feedback cards can ask the screen to do. */
class FeedbackActions(
    val onFilter: (FeedbackFilter) -> Unit,
    val onGiveFeedback: () -> Unit,
    val onResolve: (feedbackId: String) -> Unit,
    val onReopen: (feedbackId: String) -> Unit,
    val onAttach: (feedbackId: String) -> Unit,
    val onDelete: (FeedbackItem) -> Unit,
    val onOpenFile: (FileAttachment) -> Unit,
    val onRetryUpload: (fileId: String) -> Unit,
    val onDiscardUpload: (fileId: String) -> Unit,
)

/**
 * The Feedback section of chapter detail: a button for the adviser, an
 * Open/Resolved switch, and one card per piece of feedback.
 */
fun LazyListScope.feedbackSection(
    state: ChapterFeedbackUiState,
    openingFileId: String?,
    now: Instant,
    actions: FeedbackActions,
) {
    item(key = "feedback-header") {
        SectionHeader(stringResource(R.string.feedback_title), modifier = Modifier.padding(top = 10.dp))
    }
    if (state.canGiveFeedback) {
        item(key = "feedback-give") {
            GradientButton(
                text = stringResource(R.string.feedback_give),
                onClick = actions.onGiveFeedback,
                icon = Icons.Filled.Edit,
            )
        }
    }
    val total = state.openCount + state.resolvedCount
    if (total > 0) {
        item(key = "feedback-filter") {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.feedback_filter_open, state.openCount),
                    stringResource(R.string.feedback_filter_resolved, state.resolvedCount),
                ),
                selectedIndex = state.filter.ordinal,
                onSelect = { actions.onFilter(FeedbackFilter.entries[it]) },
            )
        }
    }
    if (state.items.isEmpty()) {
        item(key = "feedback-empty") {
            Text(
                stringResource(
                    when {
                        total == 0 && state.canGiveFeedback -> R.string.feedback_none_adviser
                        total == 0 -> R.string.feedback_none
                        state.filter == FeedbackFilter.OPEN -> R.string.feedback_all_resolved
                        else -> R.string.feedback_none_resolved
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(state.items, key = { "feedback-${it.feedback.id}" }) { item ->
        FeedbackCard(item = item, openingFileId = openingFileId, now = now, actions = actions)
    }
}

/**
 * One piece of feedback. The coloured edge says its state at a glance:
 * amber while open, green once resolved.
 */
@Composable
private fun FeedbackCard(item: FeedbackItem, openingFileId: String?, now: Instant, actions: FeedbackActions) {
    val aurora = AuroraTheme.colors
    val feedback = item.feedback
    val accent = if (feedback.resolved) aurora.statusApproved else aurora.statusRevisions
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent.copy(alpha = 0.8f)),
            )
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(feedback.authorName, photoUrl = null, size = 36.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                feedback.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            RoleBadge(feedback.authorRole)
                        }
                        Text(
                            stringResource(
                                R.string.feedback_meta,
                                feedback.versionNumber?.let { stringResource(R.string.feedback_on_version, it) }
                                    ?: stringResource(R.string.feedback_whole_chapter),
                                relativeTimeText(feedback.createdAt, now),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.canAttach || item.canDelete) {
                        AuthorMenu(
                            canAttach = item.canAttach,
                            canDelete = item.canDelete,
                            onAttach = { actions.onAttach(feedback.id) },
                            onDelete = { actions.onDelete(item) },
                        )
                    }
                }
                Text(
                    feedback.body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 10.dp),
                )
                feedback.files.forEach { file ->
                    FeedbackFileRow(
                        file = file,
                        isOpening = file.id == openingFileId,
                        onOpen = { actions.onOpenFile(file) },
                    )
                }
                item.uploads.forEach { upload ->
                    FeedbackUploadRow(
                        upload = upload,
                        onRetry = { actions.onRetryUpload(upload.fileId) },
                        onDiscard = { actions.onDiscardUpload(upload.fileId) },
                    )
                }
                if (feedback.hasPendingWrites) PendingSyncLabel()
                ResolutionRow(item = item, now = now, actions = actions)
            }
        }
    }
}

@Composable
private fun ResolutionRow(item: FeedbackItem, now: Instant, actions: FeedbackActions) {
    val aurora = AuroraTheme.colors
    val feedback = item.feedback
    if (feedback.resolved) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = aurora.statusApproved, modifier = Modifier.size(18.dp))
            Text(
                stringResource(
                    R.string.feedback_resolved_by,
                    feedback.resolverName ?: stringResource(R.string.version_former_member),
                    relativeTimeText(feedback.resolvedAt, now),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = aurora.statusApproved,
                modifier = Modifier.weight(1f),
            )
            if (item.canReopen) {
                TextButton(onClick = { actions.onReopen(feedback.id) }) { Text(stringResource(R.string.feedback_reopen)) }
            }
        }
    } else if (item.canResolve) {
        GhostButton(
            text = stringResource(R.string.feedback_resolve),
            onClick = { actions.onResolve(feedback.id) },
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}

@Composable
private fun AuthorMenu(canAttach: Boolean, canDelete: Boolean, onAttach: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canAttach) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feedback_attach)) },
                    onClick = {
                        open = false
                        onAttach()
                    },
                )
            }
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        open = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** A file inside a feedback card: a slim outlined row rather than a card in a card. */
@Composable
private fun FeedbackFileRow(file: FileAttachment, isOpening: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    InsetRow(onClick = onOpen) {
        Icon(
            ImageVector.vectorResource(R.drawable.ic_document),
            contentDescription = null,
            tint = AuroraTheme.colors.gradientEnd,
            modifier = Modifier.size(20.dp),
        )
        Text(
            file.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isOpening) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(
                Formatter.formatShortFileSize(context, file.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedbackUploadRow(upload: PendingUpload, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val aurora = AuroraTheme.colors
    val failed = upload.state == UploadState.FAILED
    InsetRow(onClick = null) {
        Icon(
            ImageVector.vectorResource(R.drawable.ic_upload),
            contentDescription = null,
            tint = if (failed) aurora.statusRevisions else aurora.gradientEnd,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(upload.fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (upload.state) {
                    UploadState.QUEUED -> stringResource(R.string.upload_waiting)
                    UploadState.UPLOADING -> stringResource(R.string.upload_uploading, upload.progressPercent)
                    UploadState.FAILED -> stringResource(
                        R.string.upload_failed,
                        stringResource((upload.error ?: DomainError.Unknown(null)).messageRes()),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) aurora.statusRevisions else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upload.state == UploadState.UPLOADING) GradientProgressBar(upload.progressPercent / 100f, height = 4.dp)
        }
        if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.upload_retry)) }
        if (upload.state != UploadState.UPLOADING) {
            IconButton(onClick = onDiscard, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.upload_discard), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InsetRow(onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .padding(end = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * The adviser's composer, in a bottom sheet so the chapter stays in view
 * behind it: which draft it's about, the feedback, an optional file, and
 * whether to send the chapter back for revisions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackComposerSheet(
    composer: FeedbackComposerState,
    versions: List<Int>,
    canRequestRevisions: Boolean,
    onVersionChange: (Int?) -> Unit,
    onBodyChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onRemoveFile: () -> Unit,
    onRequestRevisionsChange: (Boolean) -> Unit,
    onPost: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val aurora = AuroraTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.feedback_composer_title), style = MaterialTheme.typography.headlineSmall)

            if (versions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudLabel(stringResource(R.string.feedback_about))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        versions.forEachIndexed { index, version ->
                            ChoiceChip(
                                label = if (index == 0) {
                                    stringResource(R.string.feedback_version_latest, version)
                                } else {
                                    stringResource(R.string.chapter_latest_version, version)
                                },
                                selected = composer.versionNumber == version,
                                onClick = { onVersionChange(version) },
                            )
                        }
                        ChoiceChip(
                            label = stringResource(R.string.feedback_whole_chapter),
                            selected = composer.versionNumber == null,
                            onClick = { onVersionChange(null) },
                        )
                    }
                }
            }

            FormTextField(
                value = composer.body,
                onValueChange = onBodyChange,
                label = stringResource(R.string.field_feedback),
                hint = stringResource(R.string.field_feedback_hint),
                error = composer.bodyError?.let { validationMessage(Field.FEEDBACK, it) },
                enabled = !composer.isPosting,
                singleLine = false,
                maxLines = 8,
            )

            val attachment = composer.attachment
            if (attachment == null) {
                GhostButton(
                    text = stringResource(R.string.feedback_attach_file),
                    onClick = onPickFile,
                    enabled = !composer.isPosting,
                    icon = ImageVector.vectorResource(R.drawable.ic_upload),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, aurora.brandGradient, MaterialTheme.shapes.medium)
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlowDot(aurora.gradientEnd)
                    Text(
                        stringResource(
                            R.string.draft_upload_body,
                            attachment.file.name,
                            Formatter.formatShortFileSize(context, attachment.file.sizeBytes),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRemoveFile, enabled = !composer.isPosting) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.feedback_remove_file))
                    }
                }
            }

            if (canRequestRevisions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(aurora.glassFill)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlowDot(aurora.statusRevisions, size = 10.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.feedback_request_revisions), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.feedback_request_revisions_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = composer.requestRevisions,
                        onCheckedChange = onRequestRevisionsChange,
                        enabled = !composer.isPosting,
                    )
                }
            }

            GradientButton(
                text = stringResource(R.string.feedback_post),
                onClick = onPost,
                loading = composer.isPosting,
            )
        }
    }
}

@Composable
fun DeleteFeedbackDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feedback_delete_title)) },
        text = { Text(stringResource(R.string.feedback_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
