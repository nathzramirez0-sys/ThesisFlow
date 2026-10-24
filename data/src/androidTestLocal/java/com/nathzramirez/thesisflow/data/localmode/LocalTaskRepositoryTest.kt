package com.nathzramirez.thesisflow.data.localmode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskDraft
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalTaskRepositoryTest {

    private val graph = LocalModeTestGraph()
    private val tasks = graph.tasks

    @After
    fun tearDown() = graph.close()

    private fun draft(vararg assignees: String) = TaskDraft(
        title = "Draft the survey questionnaire",
        description = "For the respondents in 3.4",
        priority = TaskPriority.HIGH,
        dueAt = null,
        chapterId = null,
        assigneeIds = assignees.toSet(),
    )

    @Test
    fun finishingATaskRecordsWhoAndWhenAndReopeningClearsIt() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        val dan = team.others.single()
        val taskId = tasks.createTask(team.groupId, draft(dan)).value()

        graph.switchTo(dan)
        tasks.setStatus(team.groupId, taskId, TaskStatus.DONE).value()
        assertEquals(TaskStatus.DONE, tasks.observeTask(taskId).first()?.status)
        // Who finished it isn't on the domain model; the stats read it from the row.
        val done = graph.db.localModeDao().task(taskId)?.task
        assertEquals(dan, done?.completedBy)
        assertNotNull(done?.completedAt)

        tasks.setStatus(team.groupId, taskId, TaskStatus.TODO).value()
        val reopened = graph.db.localModeDao().task(taskId)
        assertNull(reopened?.task?.completedBy)
        assertNull(reopened?.task?.completedAt)
        assertEquals(listOf(dan), reopened?.assignees?.map { it.uid })

        val events = graph.feed(team.groupId).map { it.event }
        assertTrue(events.any { it is ActivityEvent.TaskCreated && it.taskId == taskId })
        assertEquals(
            setOf(TaskStatus.DONE, TaskStatus.TODO),
            events.mapNotNull { (it as? ActivityEvent.TaskStatusChanged)?.status }.toSet(),
        )
    }

    @Test
    fun editingATaskReplacesItsAssignees() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        val taskId = tasks.createTask(team.groupId, draft(team.others.single())).value()

        tasks.updateTask(team.groupId, taskId, draft(team.leader).copy(title = "Pilot the questionnaire")).value()

        val edited = tasks.observeTask(taskId).first()
        assertEquals("Pilot the questionnaire", edited?.title)
        assertEquals(setOf(team.leader), edited?.assigneeIds)
        assertEquals(TaskStatus.TODO, edited?.status)
    }

    @Test
    fun deletingATaskTakesItsCommentsAndAttachments() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        val taskId = tasks.createTask(team.groupId, draft(team.others.single())).value()
        graph.switchTo(team.others.single())
        tasks.addComment(team.groupId, taskId, "First 15 questions are in.").value()
        val uri = graph.pickableFile("questionnaire.pdf")
        graph.files.queueUpload(team.groupId, UploadTarget.TaskAttachment(taskId), uri, graph.files.inspect(uri).value()).value()

        assertEquals("Dan Villanueva", tasks.observeComments(taskId).first().single().authorName)
        val attachment = graph.files.observeTaskAttachments(taskId).first().single()
        val stored = graph.fileStore.fileFor(attachment.id, attachment.name)
        assertTrue(stored.exists())

        graph.switchTo(team.leader)
        tasks.deleteTask(team.groupId, taskId).value()

        assertNull(tasks.observeTask(taskId).first())
        assertTrue(tasks.observeComments(taskId).first().isEmpty())
        assertTrue(graph.files.observeTaskAttachments(taskId).first().isEmpty())
        assertFalse(stored.exists())
    }

    @Test
    fun aFileForADeletedTaskIsRefusedAndNotKept() = runTest {
        graph.person("Carla Mendoza")
        val groupId = graph.groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        val taskId = tasks.createTask(groupId, draft()).value()
        tasks.deleteTask(groupId, taskId).value()

        val uri = graph.pickableFile("late.pdf")
        val result = graph.files.queueUpload(groupId, UploadTarget.TaskAttachment(taskId), uri, graph.files.inspect(uri).value())

        assertEquals(DomainError.NotFound, result.error())
        val leftovers = File(graph.context.filesDir, "local_files").listFiles().orEmpty()
        assertTrue(leftovers.isEmpty())
    }
}
