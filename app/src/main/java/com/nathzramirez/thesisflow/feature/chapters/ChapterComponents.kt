package com.nathzramirez.thesisflow.feature.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.ui.DeadlineLabel
import com.nathzramirez.thesisflow.ui.text
import java.time.Instant
import java.time.ZoneId

/** "Due in 3 days" in the normal text colour, or "2 days overdue" in the revisions colour. */
@Composable
fun DeadlineText(chapter: Chapter, now: Instant, modifier: Modifier = Modifier) {
    val deadline = chapter.deadline
    if (deadline == null) {
        Text(
            stringResource(R.string.chapter_no_deadline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val label = DeadlineLabel.of(deadline, now, ZoneId.systemDefault(), done = chapter.status == ChapterStatus.APPROVED)
    val overdue = label is DeadlineLabel.Overdue
    Text(
        label.text(),
        style = MaterialTheme.typography.labelMedium,
        color = if (overdue) AuroraTheme.colors.statusRevisions else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Shown while an edit is still queued on the phone, e.g. offline. */
@Composable
fun PendingSyncLabel(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlowDot(AuroraTheme.colors.statusReview, size = 6.dp)
        Text(
            stringResource(R.string.chapter_waiting_sync),
            style = MaterialTheme.typography.labelSmall,
            color = AuroraTheme.colors.statusReview,
        )
    }
}
