package com.nathzramirez.thesisflow.feature.tasks

import app.cash.turbine.test
import com.nathzramirez.thesisflow.MainDispatcherRule
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Member
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.usecase.task.ChangeTaskStatusUseCase
import com.nathzramirez.thesisflow.domain.usecase.task.SaveTaskUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.feature.tasks.board.TaskBoardViewModel
import com.nathzramirez.thesisflow.feature.tasks.board.TaskFilter
import com.nathzramirez.thesisflow.feature.tasks.editor.TaskEditorViewModel
import com.nathzramirez.thesisflow.navigation.TaskBoardRoute
import com.nathzramirez.thesisflow.navigation.TaskEditorRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val GROUP = "g-1"

private fun member(uid: String, role: Role) = Member(uid, uid.replaceFirstChar(Char::uppercase), null, role, null)

private fun task(id: String, status: TaskStatus, assignees: Set<String>) = Task(
    id = id, groupId = GROUP, title = "Task $id", description = "", status = status,
    priority = TaskPriority.MEDIUM, dueAt = null, chapterId = null, assigneeIds = assignees,
    createdBy = "ana", createdAt = null, completedAt = null, hasPendingWrites = false,
)

class TaskBoardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tasks = MutableStateFlow(
        listOf(
            task("t1", TaskStatus.TODO, setOf("ben")),
            task("t2", TaskStatus.IN_PROGRESS, setOf("cara")),
            task("t3", TaskStatus.DONE, setOf("ben")),
        ),
    )
    private val auth = mockk<AuthRepository> { every { authState } returns flowOf(AuthState.SignedIn("ben")) }
    private val groups = mockk<GroupRepository> {
        every { observeGroup(GROUP) } returns flowOf(Group(GROUP, "Group 2", "", "BSIT", "UPang", Role.MEMBER, 3, null, null, null))
        every { observeMembers(GROUP) } returns flowOf(listOf(member("ana", Role.LEADER), member("ben", Role.MEMBER), member("cara", Role.MEMBER)))
    }
    private val chapters = mockk<ChapterRepository> { every { observeChapters(GROUP) } returns flowOf(emptyList()) }
    private val taskRepository = mockk<TaskRepository> {
        every { observeTasks(GROUP) } returns tasks
        every { observeTask(any()) } answers { flowOf(tasks.value.first { it.id == firstArg() }) }
        coEvery { setStatus(any(), any(), any()) } returns AppResult.Success(Unit)
    }

    private fun viewModel() = TaskBoardViewModel(
        route = TaskBoardRoute(GROUP),
        authRepository = auth,
        groupRepository = groups,
        chapterRepository = chapters,
        taskRepository = taskRepository,
        changeTaskStatus = ChangeTaskStatusUseCase(auth, groups, taskRepository),
    )

    @Test
    fun `the mine filter keeps only my tasks, across every column`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(3, expectMostRecentItem().tasks.size)
            viewModel.setFilter(TaskFilter.MINE)
            val mine = awaitItem()
            assertEquals(listOf("t1", "t3"), mine.tasks.map { it.id })
            assertEquals(3, mine.totalTasks)
        }
    }

    @Test
    fun `a member can advance only their own tasks`() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.canAdvance(state.tasks.first { it.id == "t1" }))
            assertFalse(state.canAdvance(state.tasks.first { it.id == "t2" }))
        }
    }

    @Test
    fun `advancing a done task reopens it to To do`() = runTest {
        val viewModel = viewModel()
        viewModel.advance(tasks.value.first { it.id == "t3" })
        coVerify { taskRepository.setStatus(GROUP, "t3", TaskStatus.TODO) }
    }
}

class TaskEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository> {
        every { observeGroup(GROUP) } returns flowOf(Group(GROUP, "Group 2", "", "BSIT", "UPang", Role.LEADER, 3, null, null, null))
        every { observeMembers(GROUP) } returns flowOf(
            listOf(member("ana", Role.LEADER), member("ben", Role.MEMBER), member("dr", Role.ADVISER)),
        )
    }
    private val chapters = mockk<ChapterRepository> { every { observeChapters(GROUP) } returns flowOf(emptyList()) }
    private val taskRepository = mockk<TaskRepository> {
        coEvery { createTask(any(), any()) } returns AppResult.Success("t9")
    }

    private fun viewModel() = TaskEditorViewModel(
        route = TaskEditorRoute(GROUP),
        groupRepository = groups,
        chapterRepository = chapters,
        taskRepository = taskRepository,
        saveTask = SaveTaskUseCase(groups, taskRepository),
    )

    @Test
    fun `advisers are not offered as assignees`() = runTest {
        viewModel().uiState.test {
            assertEquals(listOf("ana", "ben"), expectMostRecentItem().assignable.map { it.uid })
        }
    }

    @Test
    fun `a blank title shows a field error and nothing is saved`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            viewModel.save()
            assertEquals(ValidationError.REQUIRED, expectMostRecentItem().fieldErrors[Field.TASK_TITLE])
        }
        coVerify(exactly = 0) { taskRepository.createTask(any(), any()) }
    }

    @Test
    fun `saving a new task reports its id for navigation`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            viewModel.onTitleChange("Draft the survey questionnaire")
            viewModel.onPriorityChange(TaskPriority.HIGH)
            viewModel.toggleAssignee("ben")
            viewModel.save()
            assertEquals("t9", expectMostRecentItem().savedTaskId)
        }
        coVerify {
            taskRepository.createTask(
                GROUP,
                TaskDraft("Draft the survey questionnaire", "", TaskPriority.HIGH, null, null, setOf("ben")),
            )
        }
    }
}
