package com.nathzramirez.thesisflow.feature.activity

import com.nathzramirez.thesisflow.domain.model.Activity
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Task
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityEntriesTest {

    private fun chapter(id: String, order: Int) = Chapter(
        id = id, groupId = "g-1", order = order, title = id, status = ChapterStatus.DRAFTING,
        deadline = null, latestVersion = 1, updatedAt = null, hasPendingWrites = false,
    )

    private val task = Task(
        id = "t-1", groupId = "g-1", title = "Survey", description = "", status = TaskStatus.TODO,
        priority = TaskPriority.LOW, dueAt = null, chapterId = null, assigneeIds = emptySet(),
        createdBy = "ana", createdAt = null, completedAt = null, hasPendingWrites = false,
    )

    private fun activity(event: ActivityEvent) =
        Activity(id = "a", groupId = "g-1", actorId = "ben", actorName = "Ben", event = event, createdAt = null)

    @Test
    fun `chapters link by their position in the list`() {
        val entries = linkActivities(
            listOf(activity(ActivityEvent.DraftUploaded("ch-b", "Methodology", 2))),
            chapters = listOf(chapter("ch-a", 1), chapter("ch-b", 2)),
            tasks = emptyList(),
        )
        assertEquals(ActivityLink.ToChapter("ch-b", 2), entries.single().link)
    }

    @Test
    fun `deleted targets and member events have no link`() {
        val entries = linkActivities(
            listOf(
                activity(ActivityEvent.FeedbackPosted("ch-gone", "Old chapter", null)),
                activity(ActivityEvent.TaskStatusChanged("t-gone", "Old task", TaskStatus.DONE)),
                activity(ActivityEvent.MemberJoined(Role.ADVISER)),
                activity(ActivityEvent.TaskCreated("t-1", "Survey")),
            ),
            chapters = listOf(chapter("ch-a", 1)),
            tasks = listOf(task),
        )
        assertEquals(listOf(null, null, null, ActivityLink.ToTask("t-1")), entries.map { it.link })
    }
}
