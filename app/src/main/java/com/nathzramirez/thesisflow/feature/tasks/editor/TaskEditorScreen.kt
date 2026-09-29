package com.nathzramirez.thesisflow.feature.tasks.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.SegmentedControl
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.feature.tasks.priorityLabel
import com.nathzramirez.thesisflow.navigation.TaskEditorRoute
import com.nathzramirez.thesisflow.ui.DueDatePickerDialog
import com.nathzramirez.thesisflow.ui.messageFor
import com.nathzramirez.thesisflow.ui.messageRes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TaskEditorScreen(
    route: TaskEditorRoute,
    onBack: () -> Unit,
    onSaved: (taskId: String) -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel<TaskEditorViewModel, TaskEditorViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.savedTaskId) {
        val id = state.savedTaskId ?: return@LaunchedEffect
        viewModel.navigationHandled()
        onSaved(id)
    }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        viewModel.errorShown()
    }

    AuroraScaffold(
        topBar = {
            AuroraTopBar(
                title = stringResource(if (state.isEdit) R.string.task_editor_edit else R.string.task_editor_new),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            FullScreenLoading(Modifier.padding(padding))
            return@AuroraScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormTextField(
                            value = state.form.title,
                            onValueChange = viewModel::onTitleChange,
                            label = stringResource(R.string.field_task_title),
                            hint = stringResource(R.string.field_task_title_hint),
                            error = state.fieldErrors.messageFor(Field.TASK_TITLE),
                            enabled = !state.isSaving,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        )
                        FormTextField(
                            value = state.form.description,
                            onValueChange = viewModel::onDescriptionChange,
                            label = stringResource(R.string.field_task_description),
                            error = state.fieldErrors.messageFor(Field.TASK_DESCRIPTION),
                            enabled = !state.isSaving,
                            singleLine = false,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudLabel(stringResource(R.string.task_field_priority))
                    SegmentedControl(
                        options = TaskPriority.entries.map { priorityLabel(it) },
                        selectedIndex = state.form.priority.ordinal,
                        onSelect = { viewModel.onPriorityChange(TaskPriority.entries[it]) },
                    )
                }

                PickerRow(
                    label = stringResource(R.string.task_field_due),
                    value = state.form.dueAt?.let(dueFormatter::format) ?: stringResource(R.string.task_no_due),
                    onClick = viewModel::openDuePicker,
                    showCalendar = true,
                )
                PickerRow(
                    label = stringResource(R.string.task_field_chapter),
                    value = state.linkedChapterNumber?.let { number ->
                        stringResource(R.string.chapter_number, number) + " · " +
                            state.chapters.first { it.id == state.form.chapterId }.title
                    } ?: stringResource(R.string.task_no_chapter),
                    onClick = viewModel::openChapterPicker,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudLabel(stringResource(R.string.task_field_assignees))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.assignable.forEach { member ->
                            AssigneeChip(
                                member = member,
                                selected = member.uid in state.form.assigneeIds,
                                onClick = { viewModel.toggleAssignee(member.uid) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.task_assignees_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                GradientButton(
                    text = stringResource(if (state.isEdit) R.string.task_save_edit else R.string.task_save_new),
                    onClick = viewModel::save,
                    loading = state.isSaving,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (state.isDuePickerOpen) {
        DueDatePickerDialog(current = state.form.dueAt, onPick = viewModel::onDueChange, onDismiss = viewModel::dismissDuePicker)
    }
    if (state.isChapterPickerOpen) {
        ChapterPickerDialog(
            chapters = state.chapters,
            selectedId = state.form.chapterId,
            onPick = viewModel::onChapterChange,
            onDismiss = viewModel::dismissChapterPicker,
        )
    }
}

/** A labelled glass row that opens a picker. */
@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit, showCalendar: Boolean = false) {
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        HudLabel(label)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showCalendar) Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AuroraTheme.colors.gradientEnd)
        }
    }
}

@Composable
private fun AssigneeChip(member: Member, selected: Boolean, onClick: () -> Unit) {
    val aurora = AuroraTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) aurora.glassFillStrong else aurora.glassFill,
        border = if (selected) BorderStroke(1.5.dp, aurora.brandGradient) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(member.displayName, member.photoUrl, size = 30.dp)
            Text(
                member.displayName.ifBlank { stringResource(R.string.member_unnamed) },
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) aurora.gradientEnd else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChapterPickerDialog(
    chapters: List<Chapter>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_pick_chapter)) },
        text = {
            Column {
                ChapterOption(stringResource(R.string.task_no_chapter), selected = selectedId == null) { onPick(null) }
                chapters.forEachIndexed { index, chapter ->
                    ChapterOption(
                        stringResource(R.string.chapter_number, index + 1) + " · " + chapter.title,
                        selected = chapter.id == selectedId,
                    ) { onPick(chapter.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChapterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlowDot(if (selected) AuroraTheme.colors.gradientEnd else MaterialTheme.colorScheme.outline, size = 10.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val dueFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
