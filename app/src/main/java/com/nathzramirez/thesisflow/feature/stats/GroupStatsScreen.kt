package com.nathzramirez.thesisflow.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.BarSegment
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowPill
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.ProgressRing
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.component.SegmentedBar
import com.nathzramirez.thesisflow.designsystem.component.statusLabel
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.designsystem.theme.SpaceGrotesk
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.GroupStats
import com.nathzramirez.thesisflow.domain.model.MemberContribution
import com.nathzramirez.thesisflow.domain.model.PersonName
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.feature.tasks.taskStatusLabel
import com.nathzramirez.thesisflow.navigation.GroupStatsRoute
import java.time.Duration
import java.time.format.DateTimeFormatter

@Composable
fun GroupStatsScreen(
    route: GroupStatsRoute,
    onBack: () -> Unit,
    viewModel: GroupStatsViewModel = hiltViewModel<GroupStatsViewModel, GroupStatsViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.stats

    AuroraScaffold(topBar = { AuroraTopBar(title = "", onBack = onBack) }) { padding ->
        if (state.isLoading || stats == null) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudLabel(state.groupName)
                    Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.displaySmall)
                }
            }
            item { Highlights(stats) }
            item { WeeklyCard(stats) }
            item { ContributionsCard(stats.contributions) }
            item { BreakdownCard(stats) }
            item {
                Text(
                    stringResource(R.string.stats_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Four numbers that answer "how are we doing?" at a glance. */
@Composable
private fun Highlights(stats: GroupStats) {
    val aurora = AuroraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.weight(1f).fillMaxHeight(), strong = true, contentPadding = PaddingValues(16.dp)) {
                HudLabel(stringResource(R.string.stats_kpi_chapters))
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProgressRing(percent = stats.progress.percent, size = 56.dp, strokeWidth = 5.dp)
                    BigNumber(stringResource(R.string.stats_of, stats.progress.approvedCount, stats.progress.chapterCount))
                }
            }
            Kpi(
                label = stringResource(R.string.stats_kpi_tasks),
                value = stringResource(R.string.stats_of, stats.tasksDone, stats.tasksTotal),
                pill = stats.overdueTasks.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.tasks_overdue_count, it, it) to aurora.statusRevisions
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(
                label = stringResource(R.string.stats_kpi_feedback),
                value = stats.openFeedback.toString(),
                pill = pluralStringResource(R.plurals.stats_resolved_count, stats.resolvedFeedback, stats.resolvedFeedback) to
                    aurora.statusApproved,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Kpi(
                label = stringResource(R.string.stats_kpi_resolution),
                value = stats.typicalResolution?.let { durationText(it) } ?: stringResource(R.string.stats_none_yet),
                pill = null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, pill: Pair<String, Color>?, modifier: Modifier) {
    GlassCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        HudLabel(label)
        BigNumber(value, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        pill?.let { (text, color) -> GlowPill(text, color) }
    }
}

@Composable
private fun BigNumber(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            brush = AuroraTheme.colors.brandGradient,
        ),
    )
}

@Composable
private fun WeeklyCard(stats: GroupStats) {
    val series = workSeries(
        tasks = stats.weeks.map { it.tasksDone },
        drafts = stats.weeks.map { it.draftsUploaded },
        resolved = stats.weeks.map { it.feedbackResolved },
    )
    val labels = stats.weeks.map { weekFormatter.format(it.weekStart) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.stats_weekly_title))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            if (stats.weeks.all { it.total == 0 }) {
                Text(
                    stringResource(R.string.stats_weekly_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StackedColumnChart(
                    series = series,
                    xLabels = labels,
                    description = stringResource(R.string.stats_weekly_description, stats.weeks.last().total),
                    // Eight dates don't fit a phone; every other one, ending with this week, does.
                    labelEvery = 2,
                )
                ChartLegend(series, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun ContributionsCard(contributions: List<MemberContribution>) {
    if (contributions.isEmpty()) return
    val series = workSeries(
        tasks = contributions.map { it.tasksDone },
        drafts = contributions.map { it.draftsUploaded },
        resolved = contributions.map { it.feedbackResolved },
    )
    // With nothing done yet the chart would be empty, so only the rows show.
    val showChart = contributions.any { it.total > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.stats_contributions_title))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            if (showChart) {
                StackedColumnChart(
                    series = series,
                    xLabels = contributions.map { PersonName.firstName(it.member.displayName).ifBlank { "?" } },
                    description = stringResource(R.string.stats_contributions_title),
                    columnSpacing = 96,
                )
                ChartLegend(series, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            }
            contributions.forEachIndexed { index, contribution ->
                if (index > 0 || showChart) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                MemberRow(contribution)
            }
        }
    }
}

@Composable
private fun MemberRow(contribution: MemberContribution) {
    val aurora = AuroraTheme.colors
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(contribution.member.displayName, contribution.member.photoUrl, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(contribution.member.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(
                    pluralStringResource(R.plurals.stats_tasks, contribution.tasksDone, contribution.tasksDone),
                    pluralStringResource(R.plurals.stats_drafts, contribution.draftsUploaded, contribution.draftsUploaded),
                    pluralStringResource(R.plurals.stats_resolved_count, contribution.feedbackResolved, contribution.feedbackResolved),
                    pluralStringResource(R.plurals.stats_comments, contribution.comments, contribution.comments),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            contribution.overdueTasks > 0 -> GlowPill(
                pluralStringResource(R.plurals.tasks_overdue_count, contribution.overdueTasks, contribution.overdueTasks),
                aurora.statusRevisions,
            )
            contribution.openTasks > 0 -> GlowPill(
                pluralStringResource(R.plurals.stats_open_count, contribution.openTasks, contribution.openTasks),
                aurora.gradientEnd,
            )
        }
    }
}

@Composable
private fun BreakdownCard(stats: GroupStats) {
    val aurora = AuroraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.stats_breakdown_title))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudLabel(stringResource(R.string.chapters_title))
                    SegmentedBar(
                        ChapterStatus.entries.map { status ->
                            BarSegment(statusLabel(status), stats.chaptersByStatus[status] ?: 0, aurora.statusColor(status))
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudLabel(stringResource(R.string.tasks_title))
                    SegmentedBar(
                        TaskStatus.entries.map { status ->
                            BarSegment(taskStatusLabel(status), stats.tasksByStatus[status] ?: 0, aurora.taskStatusColor(status))
                        },
                    )
                }
            }
        }
    }
}

/** The three kinds of finished work, in the same colours on every chart. */
@Composable
private fun workSeries(tasks: List<Int>, drafts: List<Int>, resolved: List<Int>): List<ChartSeries> {
    val aurora = AuroraTheme.colors
    return listOf(
        ChartSeries(stringResource(R.string.stats_series_tasks), aurora.gradientEnd, tasks),
        ChartSeries(stringResource(R.string.stats_series_drafts), aurora.gradientStart, drafts),
        ChartSeries(stringResource(R.string.stats_series_feedback), aurora.statusApproved, resolved),
    )
}

/** Hours for the same day, days after that. */
@Composable
private fun durationText(duration: Duration): String {
    val hours = duration.toHours()
    return if (hours < 24) {
        val shown = hours.coerceAtLeast(1).toInt()
        pluralStringResource(R.plurals.stats_hours, shown, shown)
    } else {
        val days = Math.round(hours / 24.0).toInt()
        pluralStringResource(R.plurals.stats_days, days, days)
    }
}

private val weekFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
