package com.nathzramirez.thesisflow.data.localmode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Chapters, drafts and adviser feedback in the local build, including what the clean-up functions do. */
@RunWith(AndroidJUnit4::class)
class LocalChapterWorkTest {

    private val graph = LocalModeTestGraph()

    @After
    fun tearDown() = graph.close()

    private suspend fun firstChapter(groupId: String): Chapter = graph.chapters.observeChapters(groupId).first().first()

    private suspend fun uploadDraft(groupId: String, chapterId: String, note: String = "") {
        val uri = graph.pickableFile("Chapter1_Introduction.pdf")
        val info = graph.files.inspect(uri).value()
        graph.files.queueUpload(groupId, UploadTarget.ChapterDraft(chapterId, note), uri, info).value()
    }

    private suspend fun storedFile(chapterId: String, version: Int): File {
        val file = graph.chapters.observeVersions(chapterId).first().single { it.versionNumber == version }.file!!
        return graph.fileStore.fileFor(file.id, file.name)
    }

    @Test
    fun eachDraftIsTheNextVersionAndTheFirstStartsTheChapter() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        val chapter = firstChapter(team.groupId)
        graph.switchTo(team.others.single())

        uploadDraft(team.groupId, chapter.id, "First full draft")
        uploadDraft(team.groupId, chapter.id)

        val updated = graph.chapters.observeChapter(chapter.id).first()
        assertEquals(2, updated?.latestVersion)
        assertEquals(ChapterStatus.DRAFTING, updated?.status)
        val versions = graph.chapters.observeVersions(chapter.id).first()
        assertEquals(listOf(2, 1), versions.map { it.versionNumber })
        assertEquals("First full draft", versions.last().note)
        assertEquals("Dan Villanueva", versions.last().uploaderName)
        assertEquals("application/pdf", versions.last().file?.mimeType)
        assertEquals(1024L, versions.last().file?.sizeBytes)
        assertTrue(File(graph.files.localCopy(versions.last().file!!).value()).exists())
        // Starting the chapter isn't news of its own; the uploads are.
        assertEquals(
            listOf(2, 1),
            graph.feed(team.groupId).mapNotNull { (it.event as? ActivityEvent.DraftUploaded)?.versionNumber },
        )
        assertTrue(graph.feed(team.groupId).none { it.event is ActivityEvent.ChapterStatusChanged })
    }

    @Test
    fun aStatusChangeIsNewsButTheSameStatusIsNot() = runTest {
        graph.person("Carla Mendoza")
        val groupId = graph.groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        val chapter = firstChapter(groupId)

        graph.chapters.setStatus(groupId, chapter.id, ChapterStatus.FOR_REVIEW).value()
        graph.chapters.setStatus(groupId, chapter.id, ChapterStatus.FOR_REVIEW).value()

        assertEquals(
            listOf(ActivityEvent.ChapterStatusChanged(chapter.id, chapter.title, ChapterStatus.FOR_REVIEW)),
            graph.feed(groupId).map { it.event },
        )
    }

    @Test
    fun adviserFeedbackCanSendTheChapterBackForRevisions() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER, "Prof. Liza Reyes" to Role.ADVISER)
        val chapter = firstChapter(team.groupId)
        graph.switchTo(team.others[0])
        uploadDraft(team.groupId, chapter.id)

        graph.switchTo(team.others[1])
        val feedbackId = graph.feedback
            .postFeedback(team.groupId, chapter.id, "Cite your sources in 1.2.", 1, moveToRevisions = true)
            .value()

        assertEquals(ChapterStatus.REVISIONS, graph.chapters.observeChapter(chapter.id).first()?.status)
        val posted = graph.feedback.observeFeedbackById(feedbackId).first()
        assertEquals(Role.ADVISER, posted?.authorRole)
        assertEquals("Prof. Liza Reyes", posted?.authorName)
        assertEquals(1, posted?.versionNumber)
        assertEquals(mapOf(chapter.id to 1), graph.feedback.observeOpenCounts(team.groupId).first())
        val events = graph.feed(team.groupId).map { it.event }
        assertTrue(ActivityEvent.FeedbackPosted(chapter.id, chapter.title, 1) in events)
        assertTrue(ActivityEvent.ChapterStatusChanged(chapter.id, chapter.title, ChapterStatus.REVISIONS) in events)
    }

    @Test
    fun resolvingRecordsWhoDidItAndReopeningClearsIt() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER, "Prof. Liza Reyes" to Role.ADVISER)
        val chapter = firstChapter(team.groupId)
        val (dan, reyes) = team.others
        graph.switchTo(reyes)
        val feedbackId = graph.feedback.postFeedback(team.groupId, chapter.id, "Tighten 1.3.", null, false).value()

        graph.switchTo(dan)
        graph.feedback.setResolved(team.groupId, chapter.id, feedbackId, resolved = true).value()
        val resolved = graph.feedback.observeFeedbackById(feedbackId).first()
        assertTrue(resolved?.resolved == true)
        assertEquals(dan, resolved?.resolvedBy)
        assertEquals("Dan Villanueva", resolved?.resolverName)
        assertTrue(graph.feedback.observeOpenCounts(team.groupId).first().isEmpty())

        graph.switchTo(reyes)
        graph.feedback.setResolved(team.groupId, chapter.id, feedbackId, resolved = false).value()
        val reopened = graph.feedback.observeFeedbackById(feedbackId).first()
        assertFalse(reopened?.resolved == true)
        assertNull(reopened?.resolvedBy)
        assertNull(reopened?.resolvedAt)
        val events = graph.feed(team.groupId).map { it.event }
        assertTrue(ActivityEvent.FeedbackResolved(chapter.id, chapter.title) in events)
        assertTrue(ActivityEvent.FeedbackReopened(chapter.id, chapter.title) in events)
    }

    @Test
    fun deletingAChapterTakesItsDraftsFeedbackAndFiles() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER, "Prof. Liza Reyes" to Role.ADVISER)
        val chapter = firstChapter(team.groupId)
        graph.switchTo(team.others[0])
        uploadDraft(team.groupId, chapter.id)
        val draft = storedFile(chapter.id, 1)
        graph.switchTo(team.others[1])
        val feedbackId = graph.feedback.postFeedback(team.groupId, chapter.id, "See my notes.", 1, false).value()
        val notesUri = graph.pickableFile("markup.pdf")
        graph.files.queueUpload(
            team.groupId, UploadTarget.FeedbackFile(chapter.id, feedbackId), notesUri, graph.files.inspect(notesUri).value(),
        ).value()
        val notes = graph.feedback.observeFeedbackById(feedbackId).first()!!.files.single()
        val notesFile = graph.fileStore.fileFor(notes.id, notes.name)
        assertTrue(draft.exists() && notesFile.exists())

        graph.switchTo(team.leader)
        graph.chapters.deleteChapter(team.groupId, chapter.id).value()

        assertNull(graph.chapters.observeChapter(chapter.id).first())
        assertTrue(graph.chapters.observeVersions(chapter.id).first().isEmpty())
        assertTrue(graph.feedback.observeFeedback(chapter.id).first().isEmpty())
        assertTrue(graph.db.localModeDao().filesOfChapter(chapter.id).isEmpty())
        assertFalse(draft.exists())
        assertFalse(notesFile.exists())
    }

    @Test
    fun everyChapterMeansEveryChapterOfMyGroups() = runTest {
        graph.person("Carla Mendoza")
        graph.groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        graph.person("Eve Santos")
        val evesGroup = graph.groups.createGroup("Team Eve", "Another thesis", "BSIT", "UPang").value()

        val seen = graph.chapters.observeAllChapters().first()
        assertEquals(5, seen.size)
        assertTrue(seen.all { it.groupId == evesGroup })
    }
}
