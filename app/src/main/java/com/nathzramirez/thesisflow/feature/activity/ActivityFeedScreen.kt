package com.nathzramirez.thesisflow.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.navigation.ActivityFeedRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ActivityFeedScreen(
    route: ActivityFeedRoute,
    onBack: () -> Unit,
    onOpen: (ActivityLink) -> Unit,
    viewModel: ActivityFeedViewModel = hiltViewModel<ActivityFeedViewModel, ActivityFeedViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = remember { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }
    // Entries without a server time yet sort as today; they are seconds old.
    val days = remember(state.entries) {
        state.entries.groupBy { entry -> (entry.activity.createdAt ?: now).atZone(zone).toLocalDate() }.toList()
    }

    AuroraScaffold(topBar = { AuroraTopBar(title = "", onBack = onBack) }) { padding ->
        when {
            state.isLoading -> FullScreenLoading(Modifier.padding(padding))
            state.entries.isEmpty() -> MessageScreen(
                title = stringResource(R.string.activity_screen_title),
                body = stringResource(R.string.activity_none),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        HudLabel(state.groupName)
                        Text(stringResource(R.string.activity_screen_title), style = MaterialTheme.typography.displaySmall)
                    }
                }
                items(days, key = { (date, _) -> date.toString() }) { (date, entries) ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudLabel(dayLabel(date, now.atZone(zone).toLocalDate()), color = AuroraTheme.colors.gradientEnd)
                        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            entries.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        Modifier.padding(start = 64.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                ActivityRow(entry = entry, now = now, onOpen = onOpen)
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.activity_kept_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.time_today)
    today.minusDays(1) -> stringResource(R.string.time_yesterday)
    else -> dayFormatter.format(date)
}
