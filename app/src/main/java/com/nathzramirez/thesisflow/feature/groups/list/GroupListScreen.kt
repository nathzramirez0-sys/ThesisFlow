package com.nathzramirez.thesisflow.feature.groups.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.GradientFab
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.designsystem.component.ProgressRing
import com.nathzramirez.thesisflow.designsystem.component.RoleBadge

@Composable
fun GroupListScreen(
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: GroupListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loaded = state as? GroupListUiState.Loaded

    AuroraScaffold(
        topBar = {
            AuroraTopBar(title = "", actions = {
                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Filled.AccountCircle, stringResource(R.string.open_profile))
                }
            })
        },
        floatingActionButton = {
            if (loaded?.groups?.isNotEmpty() == true) {
                GradientFab(text = stringResource(R.string.groups_new), icon = Icons.Filled.Add, onClick = onCreateGroup)
            }
        },
    ) { padding ->
        when {
            loaded == null -> FullScreenLoading(Modifier.padding(padding))
            loaded.groups.isEmpty() -> MessageScreen(
                title = stringResource(R.string.groups_empty_title),
                body = stringResource(R.string.groups_empty_body),
                icon = ImageVector.vectorResource(R.drawable.ic_group),
                modifier = Modifier.padding(padding),
            ) {
                GradientButton(text = stringResource(R.string.groups_create), onClick = onCreateGroup)
                GhostButton(
                    text = stringResource(R.string.groups_join),
                    onClick = onJoinGroup,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    // Room for the FAB so it never covers the last card.
                    bottom = padding.calculateBottomPadding() + 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Header(loaded.firstName) }
                items(loaded.groups, key = { it.group.id }) { summary ->
                    GroupCard(summary = summary, onClick = { onGroupClick(summary.group.id) })
                }
            }
        }
    }
}

@Composable
private fun Header(firstName: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudLabel(stringResource(R.string.groups_title))
        Text(
            if (firstName.isBlank()) stringResource(R.string.groups_title)
            else stringResource(R.string.groups_greeting, firstName),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            stringResource(R.string.groups_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupCard(summary: GroupSummary, onClick: () -> Unit) {
    val group = summary.group
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    group.thesisTitle.ifBlank { stringResource(R.string.groups_no_title) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoleBadge(group.myRole)
                    Text(
                        pluralStringResource(R.plurals.groups_member_count, group.memberCount, group.memberCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ProgressRing(percent = summary.progress.percent, size = 68.dp, strokeWidth = 6.dp)
        }
    }
}
