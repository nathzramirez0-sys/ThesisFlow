package com.nathzramirez.thesisflow.feature.tasks.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.FullScreenLoading
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.GradientFab
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.MessageScreen
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.component.SegmentedControl
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.feature.tasks.ChoiceChip
import com.nathzramirez.thesisflow.feature.tasks.TaskCard
import com.nathzramirez.thesisflow.feature.tasks.taskStatusLabel
import com.nathzramirez.thesisflow.navigation.TaskBoardRoute
import com.nathzramirez.thesisflow.ui.messageRes
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun TaskBoardScreen(
    route: TaskBoardRoute,
    onBack: () -> Unit,
    onTaskClick: (String) -> Unit,
    onNewTask: () -> Unit,
    viewModel: TaskBoardViewModel = hiltViewModel<TaskBoardViewModel, TaskBoardViewModel.Factory> {
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
            if (state.canCreate && state.totalTasks > 0) {
                GradientFab(text = stringResource(R.string.tasks_new), icon = Icons.Filled.Add, onClick = onNewTask)
            }
        },
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(Modifier.padding(padding))
            state.totalTasks == 0 -> MessageScreen(
                title = stringResource(R.string.tasks_empty_title),
                body = stringResource(if (state.canCreate) R.string.tasks_empty_body_leader else R.string.tasks_empty_body),
                icon = ImageVector.vectorResource(R.drawable.ic_document),
                modifier = Modifier.padding(padding),
            ) {
                if (state.canCreate) GradientButton(text = stringResource(R.string.tasks_new), onClick = onNewTask)
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                Header(state, onViewChange = viewModel::setView, onFilterChange = viewModel::setFilter)
                val bottom = padding.calculateBottomPadding() + 104.dp
                when (state.view) {
                    TaskBoardView.LIST -> TaskList(state, now, bottom, onTaskClick, viewModel::advance, Modifier.weight(1f))
                    TaskBoardView.BOARD -> TaskKanban(state, now, bottom, onTaskClick, viewModel::advance, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: TaskBoardUiState,
    onViewChange: (TaskBoardView) -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HudLabel(state.groupName)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            SegmentedControl(
                options = listOf(stringResource(R.string.tasks_view_list), stringResource(R.string.tasks_view_board)),
                selectedIndex = state.view.ordinal,
                onSelect = { onViewChange(TaskBoardView.entries[it]) },
                modifier = Modifier.width(176.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = stringResource(R.string.tasks_filter_all),
                selected = state.filter == TaskFilter.ALL,
                onClick = { onFilterChange(TaskFilter.ALL) },
            )
            ChoiceChip(
                label = stringResource(R.string.tasks_filter_mine),
                selected = state.filter == TaskFilter.MINE,
                onClick = { onFilterChange(TaskFilter.MINE) },
            )
        }
    }
}

/** Every visible task, grouped under its column heading. */
@Composable
private fun TaskList(
    state: TaskBoardUiState,
    now: Instant,
    bottomPadding: Dp,
    onTaskClick: (String) -> Unit,
    onAdvance: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.tasks.isEmpty()) {
            item { EmptyFilterText() }
        }
        TaskStatus.entries.forEach { status ->
            val column = state.inColumn(status)
            if (column.isEmpty()) return@forEach
            item(key = "header-$status") {
                SectionHeader("${taskStatusLabel(status)} · ${column.size}", modifier = Modifier.padding(top = 8.dp))
            }
            items(column, key = { it.id }) { task -> Card(state, task, now, onTaskClick, onAdvance) }
        }
    }
}

/**
 * One swipeable column per status. The next column peeks in from the right, and
 * the segmented control above jumps between them. Cards move with their action
 * button rather than drag-and-drop, which works the same with TalkBack.
 */
@Composable
private fun TaskKanban(
    state: TaskBoardUiState,
    now: Instant,
    bottomPadding: Dp,
    onTaskClick: (String) -> Unit,
    onAdvance: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuses = TaskStatus.entries
    val pagerState = rememberPagerState { statuses.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        SegmentedControl(
            options = statuses.map { "${taskStatusLabel(it)} ${state.inColumn(it).size}" },
            selectedIndex = pagerState.currentPage,
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 20.dp, end = 44.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp),
        ) { page ->
            val status = statuses[page]
            val column = state.inColumn(status)
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowDot(AuroraTheme.colors.taskStatusColor(status), size = 10.dp)
                        HudLabel("${taskStatusLabel(status)} · ${column.size}", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                if (column.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.tasks_column_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(column, key = { it.id }) { task -> Card(state, task, now, onTaskClick, onAdvance) }
            }
        }
    }
}

@Composable
private fun Card(state: TaskBoardUiState, task: Task, now: Instant, onTaskClick: (String) -> Unit, onAdvance: (Task) -> Unit) {
    TaskCard(
        task = task,
        assignees = state.assigneesOf(task),
        chapterNumber = task.chapterId?.let(state.chapterNumbers::get),
        now = now,
        canAdvance = state.canAdvance(task),
        onAdvance = { onAdvance(task) },
        onClick = { onTaskClick(task.id) },
    )
}

@Composable
private fun EmptyFilterText() {
    Text(
        stringResource(R.string.tasks_empty_mine),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
