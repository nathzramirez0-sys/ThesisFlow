package com.nathzramirez.thesisflow.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.roleLabel
import com.nathzramirez.thesisflow.designsystem.component.statusLabel
import com.nathzramirez.thesisflow.designsystem.theme.AuroraColors
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.Activity
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.feature.tasks.taskStatusLabel
import com.nathzramirez.thesisflow.ui.relativeTimeText
import java.time.Instant

/**
 * One line of the feed: who, what, when. The dot on the avatar takes the
 * colour of what happened, e.g. green for an approval or a resolved comment.
 */
@Composable
fun ActivityRow(entry: ActivityEntry, now: Instant, onOpen: (ActivityLink) -> Unit, modifier: Modifier = Modifier) {
    val activity = entry.activity
    val link = entry.link
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (link != null) Modifier.clickable { onOpen(link) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            Avatar(activity.actorName, photoUrl = null, size = 36.dp)
            GlowDot(
                eventColor(activity.event, AuroraTheme.colors),
                size = 9.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(activitySentence(activity), style = MaterialTheme.typography.bodyMedium)
            Text(
                relativeTimeText(activity.createdAt, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (link != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun eventColor(event: ActivityEvent, aurora: AuroraColors): Color = when (event) {
    is ActivityEvent.MemberJoined -> aurora.gradientEnd
    ActivityEvent.MemberLeft -> aurora.statusNotStarted
    is ActivityEvent.ChapterStatusChanged -> aurora.statusColor(event.status)
    is ActivityEvent.DraftUploaded -> aurora.statusDrafting
    is ActivityEvent.FeedbackPosted, is ActivityEvent.FeedbackReopened -> aurora.statusRevisions
    is ActivityEvent.FeedbackResolved -> aurora.statusApproved
    is ActivityEvent.TaskCreated -> aurora.gradientStart
    is ActivityEvent.TaskStatusChanged -> aurora.taskStatusColor(event.status)
}

/** The sentence with the actor's name in bold. */
@Composable
private fun activitySentence(activity: Activity): AnnotatedString {
    val actor = activity.actorName.ifBlank { stringResource(R.string.activity_someone) }
    val text = when (val event = activity.event) {
        is ActivityEvent.MemberJoined -> stringResource(R.string.activity_joined, actor, roleLabel(event.role))
        ActivityEvent.MemberLeft -> stringResource(R.string.activity_left, actor)
        is ActivityEvent.ChapterStatusChanged -> if (event.status == ChapterStatus.APPROVED) {
            stringResource(R.string.activity_chapter_approved, actor, event.chapterTitle)
        } else {
            stringResource(R.string.activity_chapter_status, actor, event.chapterTitle, statusLabel(event.status))
        }
        is ActivityEvent.DraftUploaded ->
            stringResource(R.string.activity_draft_uploaded, actor, event.chapterTitle, event.versionNumber)
        is ActivityEvent.FeedbackPosted -> event.versionNumber?.let {
            stringResource(R.string.activity_feedback_on_version, actor, event.chapterTitle, it)
        } ?: stringResource(R.string.activity_feedback_posted, actor, event.chapterTitle)
        is ActivityEvent.FeedbackResolved -> stringResource(R.string.activity_feedback_resolved, actor, event.chapterTitle)
        is ActivityEvent.FeedbackReopened -> stringResource(R.string.activity_feedback_reopened, actor, event.chapterTitle)
        is ActivityEvent.TaskCreated -> stringResource(R.string.activity_task_created, actor, event.taskTitle)
        is ActivityEvent.TaskStatusChanged -> if (event.status == TaskStatus.DONE) {
            stringResource(R.string.activity_task_done, actor, event.taskTitle)
        } else {
            stringResource(R.string.activity_task_status, actor, event.taskTitle, taskStatusLabel(event.status))
        }
    }
    return buildAnnotatedString {
        append(text)
        val start = text.indexOf(actor)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + actor.length)
    }
}
