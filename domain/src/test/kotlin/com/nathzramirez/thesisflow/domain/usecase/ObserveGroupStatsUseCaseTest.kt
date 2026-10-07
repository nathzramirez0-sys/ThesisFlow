package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.model.chapter
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.usecase.stats.ObserveGroupStatsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ObserveGroupStatsUseCaseTest {

    private val tasks = MutableStateFlow(listOf(task(TaskStatus.TODO, setOf("ben"))))

    private val observeStats = ObserveGroupStatsUseCase(
        groupRepository = mockk<GroupRepository> {
            every { observeMembers(GROUP_ID) } returns flowOf(listOf(member("ben", Role.MEMBER)))
        },
        chapterRepository = mockk<ChapterRepository> {
            every { observeChapters(GROUP_ID) } returns flowOf(listOf(chapter(ChapterStatus.FOR_REVIEW)))
            every { observeGroupVersions(GROUP_ID) } returns flowOf(emptyList())
        },
        taskRepository = mockk<TaskRepository> {
            every { observeTasks(GROUP_ID) } returns tasks
            every { observeGroupComments(GROUP_ID) } returns flowOf(emptyList())
        },
        feedbackRepository = mockk<FeedbackRepository> {
            every { observeGroupFeedback(GROUP_ID) } returns flowOf(emptyList())
        },
    )

    private val clock = { Instant.parse("2026-10-07T04:00:00Z") }

    @Test
    fun `stats follow the cache as work gets done`() = runTest {
        val flow = observeStats(GROUP_ID, ZoneId.of("Asia/Manila"), clock)
        assertEquals(0, flow.first().contributions.single().tasksDone)

        val seen = mutableListOf<Int>()
        val job = launch { flow.take(2).toList().mapTo(seen) { it.tasksDone } }
        testScheduler.runCurrent()
        tasks.value = listOf(finished())
        job.join()

        assertEquals(listOf(0, 1), seen)
    }

    private fun finished(): Task =
        task(TaskStatus.DONE, setOf("ben")).copy(completedAt = Instant.parse("2026-10-06T02:00:00Z"))
}
