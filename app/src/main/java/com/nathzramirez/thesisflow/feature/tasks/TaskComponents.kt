package com.nathzramirez.thesisflow.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowPill
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.feature.chapters.PendingSyncLabel
import com.nathzramirez.thesisflow.ui.DeadlineLabel
import com.nathzramirez.thesisflow.ui.text
import java.time.Instant
import java.time.ZoneId

/** Where the card's action button sends a task: forward, or back to To do once done. */
fun TaskStatus.afterAdvance(): TaskStatus = next ?: TaskStatus.TODO

@Composable
fun taskStatusLabel(status: TaskStatus): String = stringResource(
    when (status) {
        TaskStatus.TODO -> R.string.task_status_todo
        TaskStatus.IN_PROGRESS -> R.string.task_status_in_progress
        TaskStatus.DONE -> R.string.task_status_done
    },
)

@Composable
fun priorityLabel(priority: TaskPriority): String = stringResource(
    when (priority) {
        TaskPriority.LOW -> R.string.task_priority_low
        TaskPriority.MEDIUM -> R.string.task_priority_medium
        TaskPriority.HIGH -> R.string.task_priority_high
    },
)

@Composable
fun TaskStatusPill(status: TaskStatus, modifier: Modifier = Modifier) {
    GlowPill(taskStatusLabel(status), AuroraTheme.colors.taskStatusColor(status), modifier)
}

@Composable
fun PriorityPill(priority: TaskPriority, modifier: Modifier = Modifier) {
    GlowPill(
        stringResource(R.string.task_priority_label, priorityLabel(priority)),
        AuroraTheme.colors.priorityColor(priority),
        modifier,
    )
}

/** "Due tomorrow", or "2 days overdue" in the warning colour. Nothing when there's no due date. */
@Composable
fun TaskDueText(task: Task, now: Instant, modifier: Modifier = Modifier) {
    val due = task.dueAt ?: return
    val label = DeadlineLabel.of(due, now, ZoneId.systemDefault(), done = task.status == TaskStatus.DONE)
    Text(
        label.text(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = if (label is DeadlineLabel.Overdue) AuroraTheme.colors.statusRevisions
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Overlapping avatars of the people assigned, or "Unassigned". */
@Composable
fun AssigneeStack(assignees: List<Member>, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    if (assignees.isEmpty()) {
        Text(
            stringResource(R.string.task_unassigned),
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(-(size / 3))) {
        assignees.take(4).forEach { member -> Avatar(member.displayName, member.photoUrl, size = size) }
    }
}

/** The small action on a card: Start, Mark done, or Reopen. */
@Composable
fun AdvanceButton(status: TaskStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val target = status.afterAdvance()
    val color = AuroraTheme.colors.taskStatusColor(target)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(
            stringResource(
                when (status) {
                    TaskStatus.TODO -> R.string.task_action_start
                    TaskStatus.IN_PROGRESS -> R.string.task_action_finish
                    TaskStatus.DONE -> R.string.task_action_reopen
                },
            ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * A task on the list or the board: a priority bar down the left edge, the title
 * (struck through once done), due date, assignees, and the advance action.
 */
@Composable
fun TaskCard(
    task: Task,
    assignees: List<Member>,
    chapterNumber: Int?,
    now: Instant,
    canAdvance: Boolean,
    onAdvance: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = task.status == TaskStatus.DONE
    val priorityColor = AuroraTheme.colors.priorityColor(task.priority)
    GlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(priorityColor.copy(alpha = if (done) 0.35f else 1f)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudLabel(priorityLabel(task.priority), color = priorityColor)
                    if (chapterNumber != null) HudLabel(stringResource(R.string.task_chapter_short, chapterNumber))
                }
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (done) 0.6f else 1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskDueText(task, now, modifier = Modifier.weight(1f))
                    if (task.dueAt == null) Spacer(Modifier.weight(1f))
                    AssigneeStack(assignees)
                }
                if (task.hasPendingWrites) PendingSyncLabel()
                if (canAdvance) AdvanceButton(task.status, onAdvance)
            }
        }
    }
}

/** A selectable pill for filters: glass when off, lit with the brand gradient border when on. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val aurora = AuroraTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) aurora.glassFillStrong else aurora.glassFill,
        border = if (selected) BorderStroke(1.dp, aurora.brandGradient)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) aurora.gradientEnd else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
