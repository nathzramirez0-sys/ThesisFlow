package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Role

@Composable
fun RoleBadge(role: Role, modifier: Modifier = Modifier) {
    val color = when (role) {
        Role.LEADER -> MaterialTheme.colorScheme.primary
        Role.MEMBER -> MaterialTheme.colorScheme.secondary
        Role.ADVISER -> MaterialTheme.colorScheme.tertiary
    }
    GlowPill(label = roleLabel(role), color = color, modifier = modifier)
}

@Composable
fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.LEADER -> R.string.role_leader
        Role.MEMBER -> R.string.role_member
        Role.ADVISER -> R.string.role_adviser
    },
)

@Composable
fun StatusPill(status: ChapterStatus, modifier: Modifier = Modifier) {
    GlowPill(label = statusLabel(status), color = AuroraTheme.colors.statusColor(status), modifier = modifier)
}

@Composable
fun statusLabel(status: ChapterStatus): String = stringResource(
    when (status) {
        ChapterStatus.NOT_STARTED -> R.string.status_not_started
        ChapterStatus.DRAFTING -> R.string.status_drafting
        ChapterStatus.FOR_REVIEW -> R.string.status_for_review
        ChapterStatus.REVISIONS -> R.string.status_revisions
        ChapterStatus.APPROVED -> R.string.status_approved
    },
)
