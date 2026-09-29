package com.nathzramirez.thesisflow.feature.chapters.list

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.FormTextField
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GlowPill
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.GradientFab
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.designsystem.component.ProgressRing
import com.nathzramirez.thesisflow.designsystem.component.StatusPill
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.designsystem.theme.SpaceGrotesk
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.feature.chapters.DeadlineText
import com.nathzramirez.thesisflow.feature.chapters.PendingSyncLabel
import com.nathzramirez.thesisflow.navigation.ChapterListRoute
import com.nathzramirez.thesisflow.ui.messageRes
import com.nathzramirez.thesisflow.ui.validationMessage
import java.time.Instant

@Composable
fun ChapterListScreen(
    route: ChapterListRoute,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, number: Int) -> Unit,
    viewModel: ChapterListViewModel = hiltViewModel<ChapterListViewModel, ChapterListViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val now = remember { Instant.now() }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        viewModel.errorShown()
    }

    AuroraScaffold(
        topBar = { AuroraTopBar(title = "", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.canManage && state.chapters.isNotEmpty()) {
                GradientFab(text = stringResource(R.string.chapter_add), icon = Icons.Filled.Add, onClick = viewModel::openAddDialog)
            }
        },
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(Modifier.padding(padding))
            state.chapters.isEmpty() -> MessageScreen(
                title = stringResource(R.string.chapters_empty_title),
                body = stringResource(
                    if (state.canManage) R.string.chapters_empty_body_leader else R.string.chapters_empty_body,
                ),
                icon = ImageVector.vectorResource(R.drawable.ic_document),
                modifier = Modifier.padding(padding),
            ) {
                if (state.canManage) {
                    GradientButton(
                        text = stringResource(R.string.chapters_add_defaults),
                        onClick = viewModel::addDefaultChapters,
                        loading = state.isBusy,
                    )
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 104.dp,
                ),
            ) {
                item { Header(state) }
                itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    TimelineRow(
                        chapter = chapter,
                        number = index + 1,
                        openFeedback = state.openFeedback[chapter.id] ?: 0,
                        isFirst = index == 0,
                        isLast = index == state.chapters.lastIndex,
                        now = now,
                        canManage = state.canManage,
                        onClick = { onChapterClick(chapter.id, index + 1) },
                        onRename = { viewModel.openRenameDialog(chapter) },
                        onDelete = { viewModel.askDelete(chapter) },
                    )
                }
            }
        }
    }

    state.titleDialog?.let { dialog ->
        TitleDialog(
            state = dialog,
            onTitleChange = viewModel::onTitleChange,
            onSave = viewModel::saveTitle,
            onDismiss = viewModel::dismissTitleDialog,
        )
    }
    state.confirmDelete?.let { chapter ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.chapter_delete_title, chapter.title)) },
            text = { Text(stringResource(R.string.chapter_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Header(state: ChapterListUiState) {
    Row(
        modifier = Modifier.padding(bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudLabel(state.groupName)
            Text(stringResource(R.string.chapters_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.chapters_progress, state.progress.approvedCount, state.progress.chapterCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressRing(percent = state.progress.percent, size = 96.dp, strokeWidth = 8.dp)
    }
}

/**
 * One chapter on the timeline: a glowing node in its status colour on a
 * vertical rail, and a glass card with the details.
 */
@Composable
private fun TimelineRow(
    chapter: Chapter,
    number: Int,
    openFeedback: Int,
    isFirst: Boolean,
    isLast: Boolean,
    now: Instant,
    canManage: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val statusColor = aurora.statusColor(chapter.status)
    val rail = MaterialTheme.colorScheme.outline
    val nodeY = 34.dp

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val y = nodeY.toPx()
                    if (!isFirst) drawLine(rail, Offset(x, 0f), Offset(x, y), strokeWidth = 2f)
                    if (!isLast) {
                        drawLine(
                            Brush.verticalGradient(listOf(statusColor.copy(alpha = 0.6f), rail), startY = y, endY = size.height),
                            Offset(x, y), Offset(x, size.height), strokeWidth = 2f,
                        )
                    }
                },
        ) {
            GlowDot(
                statusColor,
                size = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = nodeY - 6.dp),
            )
        }
        GlassCard(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 14.dp),
            contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 6.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "%02d".format(number),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                brush = aurora.brandGradient,
                            ),
                        )
                        if (chapter.latestVersion > 0) {
                            HudLabel(stringResource(R.string.chapter_latest_version, chapter.latestVersion))
                        }
                    }
                    Text(chapter.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusPill(chapter.status)
                        DeadlineText(chapter, now)
                    }
                    if (openFeedback > 0) {
                        GlowPill(
                            pluralStringResource(R.plurals.feedback_open_count, openFeedback, openFeedback),
                            aurora.statusRevisions,
                        )
                    }
                    if (chapter.hasPendingWrites) PendingSyncLabel()
                }
                if (canManage) ChapterMenu(onRename = onRename, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun ChapterMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = {
                    open = false
                    onRename()
                },
            )
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

@Composable
private fun TitleDialog(
    state: TitleDialogState,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (state.chapterId == null) R.string.chapter_add_title else R.string.chapter_rename_title))
        },
        text = {
            FormTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = stringResource(R.string.field_chapter_title),
                hint = stringResource(R.string.field_chapter_title_hint),
                error = state.error?.let { validationMessage(Field.CHAPTER_TITLE, it) },
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(stringResource(if (state.chapterId == null) R.string.action_add else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
