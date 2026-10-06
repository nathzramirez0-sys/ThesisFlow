package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Countdown
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.DefenseSchedule
import com.nathzramirez.thesisflow.domain.model.ReminderPlanner
import com.nathzramirez.thesisflow.domain.model.ReminderSubject
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.model.Urgency
import com.nathzramirez.thesisflow.domain.model.chapter
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.ReminderLog
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.group.SetDefenseDateUseCase
import com.nathzramirez.thesisflow.domain.usecase.reminder.CollectDueRemindersUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private val manila: ZoneId = ZoneId.of("Asia/Manila")

/** Monday 5 October 2026, 7:00 in Manila: when the daily reminder runs. */
private val now: Instant = ZonedDateTime.of(2026, 10, 5, 7, 0, 0, 0, manila).toInstant()

private fun onDay(day: Int, hour: Int = 23, minute: Int = 59): Instant =
    ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, manila).toInstant()

class DefenseScheduleTest {

    @Test
    fun `the next defense is the earliest one still ahead`() {
        val group = group(Role.MEMBER, 4).copy(proposalDefenseAt = onDay(1, 9, 0), finalDefenseAt = onDay(20, 9, 0))
        assertEquals(DefenseKind.FINAL, DefenseSchedule.next(group, now)?.kind)
        assertNull(DefenseSchedule.next(group, onDay(21)))
    }

    @Test
    fun `the countdown rounds up to the minute and stops at zero`() {
        val at = now.plusSeconds(26 * 3600 + 30 * 60 + 10)
        assertEquals(Countdown(days = 1, hours = 2, minutes = 31), Countdown.until(at, now))
        assertEquals(Countdown(0, 0, 0), Countdown.until(now.minusSeconds(60), now))
    }
}

class ReminderPlannerTest {

    private val groups = listOf(group(Role.MEMBER, 4))

    private fun planTasks(vararg dueDays: Int, status: TaskStatus = TaskStatus.TODO, assignees: Set<String> = setOf("ben")) =
        dueDays.flatMap { day ->
            ReminderPlanner.plan(now, manila, "ben", groups, emptyList(), listOf(task(status, assignees, onDay(day))))
        }

    @Test
    fun `assigned tasks remind the day before, on the day, and once overdue`() {
        assertEquals(
            listOf(Urgency.TOMORROW, Urgency.TODAY, Urgency.OVERDUE),
            planTasks(6, 5, 2).map { it.urgency },
        )
        assertTrue(planTasks(7).isEmpty())
    }

    @Test
    fun `finished tasks and other people's tasks stay quiet`() {
        assertTrue(planTasks(5, status = TaskStatus.DONE).isEmpty())
        assertTrue(planTasks(5, assignees = setOf("cara")).isEmpty())
    }

    @Test
    fun `chapter deadlines start three days ahead, for students only`() {
        val due = chapter(ChapterStatus.DRAFTING, deadline = onDay(8))
        val reminder = ReminderPlanner.plan(now, manila, "ben", groups, listOf(due), emptyList()).single()
        assertEquals(Urgency.SOON, reminder.urgency)
        assertEquals(3, reminder.daysLeft)

        val asAdviser = listOf(group(Role.ADVISER, 4))
        assertTrue(ReminderPlanner.plan(now, manila, "dr", asAdviser, listOf(due), emptyList()).isEmpty())
        val approved = chapter(ChapterStatus.APPROVED, deadline = onDay(8))
        assertTrue(ReminderPlanner.plan(now, manila, "ben", groups, listOf(approved), emptyList()).isEmpty())
    }

    @Test
    fun `defenses remind a week ahead and never after the fact`() {
        fun plan(day: Int) = ReminderPlanner.plan(
            now, manila, "dr", listOf(group(Role.ADVISER, 4).copy(proposalDefenseAt = onDay(day, 9, 0))),
            emptyList(), emptyList(),
        )
        assertEquals(ReminderSubject.DefenseDay(DefenseKind.PROPOSAL), plan(12).single().subject)
        assertTrue(plan(13).isEmpty())
        assertTrue(plan(4).isEmpty())
    }

    @Test
    fun `moving the due date gives a fresh reminder`() {
        val before = planTasks(6).single()
        // The next morning the leader has pushed the task back a day: "tomorrow" again, but for a new date.
        val after = ReminderPlanner.plan(
            onDay(6, 7, 0), manila, "ben", groups, emptyList(), listOf(task(TaskStatus.TODO, setOf("ben"), onDay(7))),
        ).single()
        assertEquals(before.urgency, after.urgency)
        assertNotEquals(before.key, after.key)
    }
}

class SetDefenseDateUseCaseTest {

    private val groups = mockk<GroupRepository> {
        coEvery { setDefenseDate(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val setDefenseDate = SetDefenseDateUseCase(groups)

    @Test
    fun `only leaders set defense dates`() = runTest {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(Role.MEMBER, 4))
        assertEquals(AppResult.Failure(DomainError.PermissionDenied), setDefenseDate(GROUP_ID, DefenseKind.PROPOSAL, onDay(20)))
    }

    @Test
    fun `the final defense must come after the proposal`() = runTest {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(Role.LEADER, 4).copy(proposalDefenseAt = onDay(20, 9, 0)))

        assertEquals(
            AppResult.Failure(DomainError.InvalidInput(mapOf(Field.DEFENSE_DATE to ValidationError.FINAL_BEFORE_PROPOSAL))),
            setDefenseDate(GROUP_ID, DefenseKind.FINAL, onDay(10, 9, 0)),
        )
        assertEquals(AppResult.Success(Unit), setDefenseDate(GROUP_ID, DefenseKind.FINAL, onDay(28, 9, 0)))
        assertEquals(AppResult.Success(Unit), setDefenseDate(GROUP_ID, DefenseKind.PROPOSAL, null))
        coVerify { groups.setDefenseDate(GROUP_ID, DefenseKind.PROPOSAL, null) }
    }
}

class CollectDueRemindersUseCaseTest {

    @Test
    fun `reminders already shown are left out`() = runTest {
        val auth = mockk<AuthRepository> { every { authState } returns flowOf(AuthState.SignedIn("ben")) }
        val groups = mockk<GroupRepository> { every { observeMyGroups() } returns flowOf(listOf(group(Role.MEMBER, 4))) }
        val chapters = mockk<ChapterRepository> {
            every { observeAllChapters() } returns flowOf(listOf(chapter(ChapterStatus.DRAFTING, deadline = onDay(5))))
        }
        val tasks = mockk<TaskRepository> {
            every { observeTasks(GROUP_ID) } returns flowOf(listOf(task(TaskStatus.TODO, setOf("ben"), onDay(6))))
        }
        val log = mockk<ReminderLog> {
            coEvery { sentKeys(any()) } answers { firstArg<Collection<String>>().filter { it.startsWith("chapter:") }.toSet() }
        }

        val due = CollectDueRemindersUseCase(auth, groups, chapters, tasks, log)(now, manila)

        assertEquals(listOf(ReminderSubject.TaskDue("task-1", "Draft the survey questionnaire")), due.map { it.subject })
    }
}
