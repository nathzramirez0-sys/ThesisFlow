package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskRules
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.task.AddTaskCommentUseCase
import com.nathzramirez.thesisflow.domain.usecase.task.ChangeTaskStatusUseCase
import com.nathzramirez.thesisflow.domain.usecase.task.SaveTaskUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

fun task(
    status: TaskStatus = TaskStatus.TODO,
    assignees: Set<String> = setOf("ben"),
    dueAt: Instant? = null,
) = Task(
    id = "task-1",
    groupId = GROUP_ID,
    title = "Draft the survey questionnaire",
    description = "",
    status = status,
    priority = TaskPriority.MEDIUM,
    dueAt = dueAt,
    chapterId = null,
    assigneeIds = assignees,
    createdBy = "ana",
    createdAt = null,
    completedAt = null,
    hasPendingWrites = false,
)

private fun draft(title: String = "Draft the survey questionnaire", assignees: Set<String> = setOf("ben")) =
    TaskDraft(title, "", TaskPriority.HIGH, null, null, assignees)

class TaskRulesTest {

    @Test
    fun `members move only tasks assigned to them`() {
        assertTrue(TaskRules.canChangeStatus(Role.MEMBER, task(assignees = setOf("ben")), "ben"))
        assertFalse(TaskRules.canChangeStatus(Role.MEMBER, task(assignees = setOf("cara")), "ben"))
        assertTrue(TaskRules.canChangeStatus(Role.LEADER, task(assignees = setOf("cara")), "ana"))
        assertFalse(TaskRules.canChangeStatus(Role.ADVISER, task(assignees = setOf("dr")), "dr"))
    }

    @Test
    fun `a task is overdue only while unfinished`() {
        val due = Instant.parse("2026-10-01T15:59:59Z")
        val later = due.plusSeconds(3600)
        assertTrue(task(dueAt = due).isOverdue(later))
        assertFalse(task(dueAt = due, status = TaskStatus.DONE).isOverdue(later))
    }

    @Test
    fun `statuses advance in board order`() {
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.TODO.next)
        assertEquals(TaskStatus.DONE, TaskStatus.IN_PROGRESS.next)
        assertEquals(null, TaskStatus.DONE.next)
    }
}

class SaveTaskUseCaseTest {

    private val groups = mockk<GroupRepository> {
        every { observeMembers(GROUP_ID) } returns flowOf(
            listOf(member("ana", Role.LEADER), member("ben", Role.MEMBER), member("dr", Role.ADVISER)),
        )
    }
    private val tasks = mockk<TaskRepository> {
        coEvery { createTask(any(), any()) } returns AppResult.Success("task-9")
        coEvery { updateTask(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val saveTask = SaveTaskUseCase(groups, tasks)

    private fun givenRole(role: Role) {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(role, memberCount = 3))
    }

    @Test
    fun `only leaders create tasks`() = runTest {
        givenRole(Role.MEMBER)
        assertEquals(AppResult.Failure(DomainError.PermissionDenied), saveTask(GROUP_ID, null, draft()))
        coVerify(exactly = 0) { tasks.createTask(any(), any()) }
    }

    @Test
    fun `a blank title is rejected`() = runTest {
        givenRole(Role.LEADER)
        assertEquals(
            AppResult.Failure(DomainError.InvalidInput(mapOf(Field.TASK_TITLE to ValidationError.REQUIRED))),
            saveTask(GROUP_ID, null, draft(title = "  ")),
        )
    }

    @Test
    fun `advisers and former members are dropped from assignees`() = runTest {
        givenRole(Role.LEADER)

        assertEquals(AppResult.Success("task-9"), saveTask(GROUP_ID, null, draft(assignees = setOf("ben", "dr", "gone"))))
        coVerify { tasks.createTask(GROUP_ID, draft(assignees = setOf("ben"))) }
    }

    @Test
    fun `editing returns the existing id`() = runTest {
        givenRole(Role.LEADER)
        assertEquals(AppResult.Success("task-1"), saveTask(GROUP_ID, "task-1", draft()))
        coVerify { tasks.updateTask(GROUP_ID, "task-1", draft()) }
    }
}

class ChangeTaskStatusUseCaseTest {

    private val auth = mockk<AuthRepository> { every { authState } returns flowOf(AuthState.SignedIn("ben")) }
    private val groups = mockk<GroupRepository>()
    private val tasks = mockk<TaskRepository> {
        coEvery { setStatus(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val changeStatus = ChangeTaskStatusUseCase(auth, groups, tasks)

    @Test
    fun `an assignee can finish their task`() = runTest {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(Role.MEMBER, memberCount = 3))
        every { tasks.observeTask("task-1") } returns flowOf(task(assignees = setOf("ben")))

        assertEquals(AppResult.Success(Unit), changeStatus(GROUP_ID, "task-1", TaskStatus.DONE))
        coVerify { tasks.setStatus(GROUP_ID, "task-1", TaskStatus.DONE) }
    }

    @Test
    fun `a member cannot move someone else's task`() = runTest {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(Role.MEMBER, memberCount = 3))
        every { tasks.observeTask("task-1") } returns flowOf(task(assignees = setOf("cara")))

        assertEquals(AppResult.Failure(DomainError.PermissionDenied), changeStatus(GROUP_ID, "task-1", TaskStatus.DONE))
        coVerify(exactly = 0) { tasks.setStatus(any(), any(), any()) }
    }
}

class AddTaskCommentUseCaseTest {

    private val tasks = mockk<TaskRepository> {
        coEvery { addComment(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val addComment = AddTaskCommentUseCase(tasks)

    @Test
    fun `empty comments are not sent, others are trimmed`() = runTest {
        assertEquals(
            AppResult.Failure(DomainError.InvalidInput(mapOf(Field.COMMENT to ValidationError.REQUIRED))),
            addComment(GROUP_ID, "task-1", "   "),
        )
        assertEquals(AppResult.Success(Unit), addComment(GROUP_ID, "task-1", "  Done with part 1  "))
        coVerify { tasks.addComment(GROUP_ID, "task-1", "Done with part 1") }
    }
}
