package com.nathzramirez.thesisflow.feature.chapters.detail

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.FormTextField
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.component.StatusPill
import com.nathzramirez.thesisflow.designsystem.component.statusLabel
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.designsystem.theme.SpaceGrotesk
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.UploadRules
import com.nathzramirez.thesisflow.feature.chapters.DeadlineText
import com.nathzramirez.thesisflow.feature.chapters.PendingSyncLabel
import com.nathzramirez.thesisflow.feature.files.PendingUploadRow
import com.nathzramirez.thesisflow.navigation.ChapterDetailRoute
import com.nathzramirez.thesisflow.ui.DueDatePickerDialog
import com.nathzramirez.thesisflow.ui.messageRes
import com.nathzramirez.thesisflow.ui.validationMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ChapterDetailScreen(
    route: ChapterDetailRoute,
    onBack: () -> Unit,
    viewModel: ChapterDetailViewModel = hiltViewModel<ChapterDetailViewModel, ChapterDetailViewModel.Factory> {
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

    val chapter = state.chapter
    AuroraScaffold(
        topBar = { AuroraTopBar(title = "", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (chapter == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@AuroraScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(chapter, route.number) }
            item {
                StatusAndDeadline(
                    chapter = chapter,
                    now = now,
                    canChangeStatus = state.allowedStatuses.isNotEmpty(),
                    canSetDeadline = state.canManage,
                    onChangeStatus = viewModel::openStatusPicker,
                    onSetDeadline = viewModel::openDeadlinePicker,
                )
            }
            if (state.role != null && !state.canManage && chapter.status == ChapterStatus.APPROVED &&
                state.allowedStatuses.isEmpty()
            ) {
                item {
                    Text(
                        stringResource(R.string.chapter_status_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.drafts_title), modifier = Modifier.padding(top = 10.dp)) }
            if (state.canUpload) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton(
                            text = stringResource(R.string.draft_upload),
                            onClick = { picker.launch(UploadRules.pickerTypes.toTypedArray()) },
                            icon = ImageVector.vectorResource(R.drawable.ic_upload),
                        )
                        Text(
                            stringResource(R.string.draft_types),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
            items(state.uploads, key = { "upload-${it.fileId}" }) { upload ->
                PendingUploadRow(
                    upload = upload,
                    onRetry = { viewModel.retryUpload(upload.fileId) },
                    onDiscard = { viewModel.discardUpload(upload.fileId) },
                )
            }
            if (state.versions.isEmpty() && state.uploads.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chapter_no_versions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.versions, key = { "v${it.versionNumber}" }) { version ->
                VersionRow(
                    version = version,
                    isLatest = version.versionNumber == state.versions.first().versionNumber,
                    isOpening = version.file?.id != null && version.file?.id == state.openingFileId,
                    onOpen = { version.file?.let(viewModel::openFile) },
                )
            }
        }
    }

    if (state.isStatusPickerOpen && chapter != null) {
        StatusPickerDialog(
            options = state.allowedStatuses,
            onPick = viewModel::changeStatus,
            onDismiss = viewModel::dismissStatusPicker,
        )
    }
    if (state.isDeadlinePickerOpen && chapter != null) {
        DueDatePickerDialog(
            current = chapter.deadline,
            onPick = viewModel::setDeadline,
            onDismiss = viewModel::dismissDeadlinePicker,
        )
    }
    state.draft?.let { draft ->
        UploadDraftDialog(
            draft = draft,
            onNoteChange = viewModel::onNoteChange,
            onUpload = viewModel::queueDraft,
            onDismiss = viewModel::dismissDraft,
        )
    }
}

@Composable
private fun Header(chapter: Chapter, number: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HudLabel(stringResource(R.string.chapter_number, number), color = AuroraTheme.colors.gradientEnd)
        Text(chapter.title, style = MaterialTheme.typography.headlineMedium)
        if (chapter.hasPendingWrites) PendingSyncLabel()
    }
}

/** Two glass tiles side by side, matched in height. */
@Composable
private fun StatusAndDeadline(
    chapter: Chapter,
    now: Instant,
    canChangeStatus: Boolean,
    canSetDeadline: Boolean,
    onChangeStatus: () -> Unit,
    onSetDeadline: () -> Unit,
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(
            onClick = if (canChangeStatus) onChangeStatus else null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(16.dp),
        ) {
            HudLabel(stringResource(R.string.chapter_status_label))
            StatusPill(chapter.status, modifier = Modifier.padding(vertical = 10.dp))
            if (canChangeStatus) {
                Text(
                    stringResource(R.string.action_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = AuroraTheme.colors.gradientEnd,
                )
            }
        }
        GlassCard(
            onClick = if (canSetDeadline) onSetDeadline else null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(16.dp),
        ) {
            HudLabel(stringResource(R.string.chapter_deadline_label))
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                DeadlineText(chapter, now)
            }
            if (canSetDeadline) {
                Text(
                    stringResource(if (chapter.deadline == null) R.string.chapter_set_deadline else R.string.action_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = AuroraTheme.colors.gradientEnd,
                )
            }
        }
    }
}

@Composable
private fun VersionRow(version: ChapterVersion, isLatest: Boolean, isOpening: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val aurora = AuroraTheme.colors
    val file = version.file
    GlassCard(
        onClick = if (file != null) onOpen else null,
        strong = isLatest,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, if (isLatest) aurora.brandGradient else aurora.glassBorder, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.chapter_latest_version, version.versionNumber),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        brush = aurora.brandGradient,
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (isLatest) HudLabel(stringResource(R.string.version_latest), color = aurora.gradientEnd)
                Text(
                    file?.name ?: stringResource(R.string.version_syncing),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.version_by,
                        version.uploaderName ?: stringResource(R.string.version_former_member),
                        version.uploadedAt?.let(versionDateFormatter::format).orEmpty(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (version.note.isNotBlank()) {
                    Text(
                        version.note,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isOpening) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (file != null) {
                Text(
                    Formatter.formatShortFileSize(context, file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPickerDialog(options: List<ChapterStatus>, onPick: (ChapterStatus) -> Unit, onDismiss: () -> Unit) {
    val aurora = AuroraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chapter_change_status)) },
        text = {
            Column {
                options.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPick(status) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GlowDot(aurora.statusColor(status), size = 10.dp)
                        Text(statusLabel(status), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun UploadDraftDialog(
    draft: DraftToUpload,
    onNoteChange: (String) -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.draft_upload_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowDot(AuroraTheme.colors.gradientEnd)
                    Text(
                        stringResource(
                            R.string.draft_upload_body,
                            draft.file.name,
                            Formatter.formatShortFileSize(context, draft.file.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FormTextField(
                    value = draft.note,
                    onValueChange = onNoteChange,
                    label = stringResource(R.string.field_version_note),
                    hint = stringResource(R.string.field_version_note_hint),
                    error = draft.noteError?.let { validationMessage(Field.VERSION_NOTE, it) },
                    enabled = !draft.isQueuing,
                    singleLine = false,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpload, enabled = !draft.isQueuing) {
                Text(stringResource(R.string.draft_upload_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val versionDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
