package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.model.GroupStatsCalculator
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskComment
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.model.chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class GroupStatsTest {

    private val manila = ZoneId.of("Asia/Manila")

    /** Wednesday 7 October 2026, noon in Manila. */
    private val now = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, manila).toInstant()

    private fun at(month: Int, day: Int, hour: Int = 10): Instant =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, manila).toInstant()

    private val members = listOf(
        member("cara", Role.LEADER),
        member("ben", Role.MEMBER),
        member("dr", Role.ADVISER),
    )

    private fun version(by: String, at: Instant, number: Int = 1) = ChapterVersion(
        chapterId = "ch-1", versionNumber = number, note = "", uploadedBy = by, uploaderName = null,
        uploadedAt = at, file = null,
    )

    private fun comment(by: String) = TaskComment("c-$by", "task-1", "ok", by, by, null, false)

    private fun compute(
        tasks: List<com.nathzramirez.thesisflow.domain.model.Task> = emptyList(),
        versions: List<ChapterVersion> = emptyList(),
        feedback: List<com.nathzramirez.thesisflow.domain.model.Feedback> = emptyList(),
        comments: List<TaskComment> = emptyList(),
    ) = GroupStatsCalculator.compute(
        now, manila, members, listOf(chapter(ChapterStatus.APPROVED), chapter(ChapterStatus.DRAFTING, id = "ch-2")),
        versions, tasks, feedback, comments,
    )

    @Test
    fun `contributions cover students only, by name, crediting every assignee`() {
        val shared = task(TaskStatus.DONE, setOf("ben", "cara")).copy(completedAt = at(10, 6))
        val bensOpenLate = task(TaskStatus.TODO, setOf("ben"), dueAt = at(10, 5)).copy(id = "task-2")
        val stats = compute(
            tasks = listOf(shared, bensOpenLate),
            versions = listOf(version("ben", at(10, 5)), version("ben", at(9, 1), 2), version("cara", at(10, 6), 3)),
            comments = listOf(comment("ben"), comment("dr")),
        )

        assertEquals(listOf("Ben", "Cara"), stats.contributions.map { it.member.displayName })
        val ben = stats.contributions.first()
        assertEquals(1, ben.tasksDone)
        assertEquals(2, ben.draftsUploaded)
        assertEquals(1, ben.comments)
        assertEquals(1, ben.openTasks)
        assertEquals(1, ben.overdueTasks)
        assertEquals(1, stats.contributions.last().tasksDone)
    }

    @Test
    fun `weeks run Monday to Sunday, oldest first, ending this week`() {
        val stats = compute(
            tasks = listOf(task(TaskStatus.DONE).copy(completedAt = at(10, 5, hour = 1))),
            // Sunday night belongs to the week before.
            versions = listOf(version("ben", at(10, 4, hour = 23)), version("ben", at(7, 1), 2)),
        )

        assertEquals(GroupStatsCalculator.WEEKS, stats.weeks.size)
        assertEquals(LocalDate.of(2026, 10, 5), stats.weeks.last().weekStart)
        assertEquals(1, stats.weeks.last().tasksDone)
        assertEquals(1, stats.weeks[stats.weeks.size - 2].draftsUploaded)
        // Work older than the window isn't drawn.
        assertEquals(2, stats.weeks.sumOf { it.total })
    }

    @Test
    fun `feedback resolution uses the median, and status counts include zeros`() {
        fun resolvedIn(hours: Long, id: String) = feedback(resolved = true, resolvedBy = "ben").copy(
            id = id, createdAt = at(10, 1), resolvedAt = at(10, 1).plus(Duration.ofHours(hours)),
        )
        val stats = compute(
            feedback = listOf(resolvedIn(2, "a"), resolvedIn(10, "b"), resolvedIn(400, "c"), feedback()),
        )

        assertEquals(Duration.ofHours(10), stats.typicalResolution)
        assertEquals(1, stats.openFeedback)
        assertEquals(3, stats.contributions.first().feedbackResolved)
        assertEquals(0, stats.chaptersByStatus[ChapterStatus.FOR_REVIEW])
        // One approved (100) and one drafting (25) chapter.
        assertEquals(63, stats.progress.percent)
    }

    @Test
    fun `a new group has empty stats, not errors`() {
        val stats = compute()
        assertNull(stats.typicalResolution)
        assertEquals(0, stats.tasksTotal)
        assertEquals(0, stats.weeks.sumOf { it.total })
    }
}
