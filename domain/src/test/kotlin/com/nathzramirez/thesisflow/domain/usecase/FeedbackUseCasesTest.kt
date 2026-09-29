package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.model.FeedbackDraft
import com.nathzramirez.thesisflow.domain.model.FeedbackRules
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.model.chapter
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.feedback.DeleteFeedbackUseCase
import com.nathzramirez.thesisflow.domain.usecase.feedback.FeedbackPosted
import com.nathzramirez.thesisflow.domain.usecase.feedback.PostFeedbackUseCase
import com.nathzramirez.thesisflow.domain.usecase.feedback.SetFeedbackResolvedUseCase
import com.nathzramirez.thesisflow.domain.usecase.file.QueueUploadUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.UploadRules
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

fun feedback(resolved: Boolean = false, resolvedBy: String? = null, authorId: String = "dr") = Feedback(
    id = "fb-1",
    groupId = GROUP_ID,
    chapterId = "ch-1",
    body = "Cite the 2024 DICT survey in 1.2.",
    authorId = authorId,
    authorName = "Dr. Reyes",
    authorRole = Role.ADVISER,
    versionNumber = 2,
    resolved = resolved,
    resolvedBy = resolvedBy,
    resolverName = null,
    resolvedAt = null,
    createdAt = null,
    hasPendingWrites = false,
    files = emptyList(),
)

class FeedbackRulesTest {

    @Test
    fun `only advisers give feedback`() {
        assertTrue(FeedbackRules.canGiveFeedback(Role.ADVISER))
        assertFalse(FeedbackRules.canGiveFeedback(Role.LEADER))
        assertFalse(FeedbackRules.canGiveFeedback(Role.MEMBER))
    }

    @Test
    fun `advisers reopen anything, others only what they resolved`() {
        val resolvedByBen = feedback(resolved = true, resolvedBy = "ben")
        assertTrue(FeedbackRules.canReopen(Role.ADVISER, resolvedByBen, "dr"))
        assertTrue(FeedbackRules.canReopen(Role.MEMBER, resolvedByBen, "ben"))
        assertFalse(FeedbackRules.canReopen(Role.LEADER, resolvedByBen, "ana"))
        assertFalse(FeedbackRules.canReopen(Role.ADVISER, feedback(resolved = false), "dr"))
    }

    @Test
    fun `only the author deletes or adds files`() {
        assertTrue(FeedbackRules.canDelete(feedback(), "dr"))
        assertFalse(FeedbackRules.canDelete(feedback(), "ana"))
        assertTrue(FeedbackRules.canAttachFiles(Role.ADVISER, feedback(), "dr"))
        assertFalse(FeedbackRules.canAttachFiles(Role.ADVISER, feedback(authorId = "dr2"), "dr"))
    }
}

class PostFeedbackUseCaseTest {

    private val groups = mockk<GroupRepository>()
    private val chapters = mockk<ChapterRepository>()
    private val feedbackRepo = mockk<FeedbackRepository> {
        coEvery { postFeedback(any(), any(), any(), any(), any()) } returns AppResult.Success("fb-9")
    }
    private val files = mockk<FileRepository> {
        coEvery { queueUpload(any(), any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val postFeedback = PostFeedbackUseCase(groups, chapters, feedbackRepo, files)

    private fun given(role: Role, status: ChapterStatus = ChapterStatus.FOR_REVIEW, latestVersion: Int = 2) {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(role, memberCount = 4))
        every { chapters.observeChapter("ch-1") } returns flowOf(chapter(status).copy(latestVersion = latestVersion))
    }

    private fun draft(body: String = "  Tighten the scope.  ", version: Int? = 2, revisions: Boolean = false) =
        FeedbackDraft(body, version, revisions)

    @Test
    fun `students cannot post feedback`() = runTest {
        given(Role.LEADER)
        assertEquals(AppResult.Failure(DomainError.PermissionDenied), postFeedback(GROUP_ID, "ch-1", draft(), null))
        coVerify(exactly = 0) { feedbackRepo.postFeedback(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a blank body is rejected, others are trimmed`() = runTest {
        given(Role.ADVISER)
        assertEquals(
            AppResult.Failure(DomainError.InvalidInput(mapOf(Field.FEEDBACK to ValidationError.REQUIRED))),
            postFeedback(GROUP_ID, "ch-1", draft(body = "  "), null),
        )
        assertEquals(
            AppResult.Success(FeedbackPosted("fb-9", null)),
            postFeedback(GROUP_ID, "ch-1", draft(), null),
        )
        coVerify { feedbackRepo.postFeedback(GROUP_ID, "ch-1", "Tighten the scope.", 2, false) }
    }

    @Test
    fun `feedback on a version that doesn't exist is refused`() = runTest {
        given(Role.ADVISER, latestVersion = 2)
        assertEquals(AppResult.Failure(DomainError.NotFound), postFeedback(GROUP_ID, "ch-1", draft(version = 3), null))
    }

    @Test
    fun `requesting revisions moves the chapter unless it is already there`() = runTest {
        given(Role.ADVISER, status = ChapterStatus.FOR_REVIEW)
        postFeedback(GROUP_ID, "ch-1", draft(revisions = true), null)
        coVerify { feedbackRepo.postFeedback(GROUP_ID, "ch-1", any(), 2, true) }

        given(Role.ADVISER, status = ChapterStatus.REVISIONS)
        postFeedback(GROUP_ID, "ch-1", draft(revisions = true), null)
        coVerify { feedbackRepo.postFeedback(GROUP_ID, "ch-1", any(), 2, false) }
    }

    @Test
    fun `a rejected file stops the feedback from being posted`() = runTest {
        given(Role.ADVISER)
        coEvery { files.inspect("content://notes") } returns
            AppResult.Success(LocalFileInfo("notes.mp4", "video/mp4", 5_000))

        assertEquals(
            AppResult.Failure(DomainError.UnsupportedFileType),
            postFeedback(GROUP_ID, "ch-1", draft(), "content://notes"),
        )
        coVerify(exactly = 0) { feedbackRepo.postFeedback(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the file is queued on the new feedback`() = runTest {
        given(Role.ADVISER)
        val pdf = LocalFileInfo("Ch1_marked.pdf", UploadRules.PDF, 80_000)
        coEvery { files.inspect("content://notes") } returns AppResult.Success(pdf)

        assertEquals(
            AppResult.Success(FeedbackPosted("fb-9", null)),
            postFeedback(GROUP_ID, "ch-1", draft(), "content://notes"),
        )
        coVerify { files.queueUpload(GROUP_ID, UploadTarget.FeedbackFile("ch-1", "fb-9"), "content://notes", pdf) }
    }

    @Test
    fun `a file that fails after posting is reported without failing the post`() = runTest {
        given(Role.ADVISER)
        coEvery { files.inspect("content://notes") } returns
            AppResult.Success(LocalFileInfo("Ch1_marked.pdf", UploadRules.PDF, 80_000))
        coEvery { files.queueUpload(any(), any(), any(), any()) } returns AppResult.Failure(DomainError.FileUnreadable)

        assertEquals(
            AppResult.Success(FeedbackPosted("fb-9", DomainError.FileUnreadable)),
            postFeedback(GROUP_ID, "ch-1", draft(), "content://notes"),
        )
    }
}

class SetFeedbackResolvedUseCaseTest {

    private val auth = mockk<AuthRepository>()
    private val groups = mockk<GroupRepository>()
    private val feedbackRepo = mockk<FeedbackRepository> {
        coEvery { setResolved(any(), any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val setResolved = SetFeedbackResolvedUseCase(auth, groups, feedbackRepo)

    private fun given(uid: String, role: Role, current: Feedback) {
        every { auth.authState } returns flowOf(AuthState.SignedIn(uid))
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(role, memberCount = 4))
        every { feedbackRepo.observeFeedbackById("fb-1") } returns flowOf(current)
    }

    @Test
    fun `any member can resolve open feedback`() = runTest {
        given("ben", Role.MEMBER, feedback())
        assertEquals(AppResult.Success(Unit), setResolved(GROUP_ID, "fb-1", resolved = true))
        coVerify { feedbackRepo.setResolved(GROUP_ID, "ch-1", "fb-1", true) }
    }

    @Test
    fun `a member cannot reopen what someone else resolved`() = runTest {
        given("ben", Role.MEMBER, feedback(resolved = true, resolvedBy = "cara"))
        assertEquals(AppResult.Failure(DomainError.PermissionDenied), setResolved(GROUP_ID, "fb-1", resolved = false))
        coVerify(exactly = 0) { feedbackRepo.setResolved(any(), any(), any(), any()) }
    }

    @Test
    fun `the adviser can reopen`() = runTest {
        given("dr", Role.ADVISER, feedback(resolved = true, resolvedBy = "cara"))
        assertEquals(AppResult.Success(Unit), setResolved(GROUP_ID, "fb-1", resolved = false))
        coVerify { feedbackRepo.setResolved(GROUP_ID, "ch-1", "fb-1", false) }
    }
}

class DeleteFeedbackUseCaseTest {

    private val auth = mockk<AuthRepository>()
    private val feedbackRepo = mockk<FeedbackRepository> {
        every { observeFeedbackById("fb-1") } returns flowOf(feedback(authorId = "dr"))
        coEvery { deleteFeedback(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val deleteFeedback = DeleteFeedbackUseCase(auth, feedbackRepo)

    @Test
    fun `students cannot delete the adviser's feedback`() = runTest {
        every { auth.authState } returns flowOf(AuthState.SignedIn("ana"))
        assertEquals(AppResult.Failure(DomainError.PermissionDenied), deleteFeedback(GROUP_ID, "fb-1"))

        every { auth.authState } returns flowOf(AuthState.SignedIn("dr"))
        assertEquals(AppResult.Success(Unit), deleteFeedback(GROUP_ID, "fb-1"))
        coVerify(exactly = 1) { feedbackRepo.deleteFeedback(GROUP_ID, "ch-1", "fb-1") }
    }
}

class QueueFeedbackFileTest {

    private val groups = mockk<GroupRepository>()
    private val files = mockk<FileRepository> {
        coEvery { inspect(any()) } returns AppResult.Success(LocalFileInfo("marked.pdf", UploadRules.PDF, 1_000))
        coEvery { queueUpload(any(), any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val queueUpload = QueueUploadUseCase(groups, files)
    private val target = UploadTarget.FeedbackFile("ch-1", "fb-1")

    @Test
    fun `feedback files are for advisers only`() = runTest {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(Role.MEMBER, memberCount = 4))
        assertEquals(AppResult.Failure(DomainError.PermissionDenied), queueUpload(GROUP_ID, target, "content://f"))

        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(Role.ADVISER, memberCount = 4))
        assertEquals(AppResult.Success(Unit), queueUpload(GROUP_ID, target, "content://f"))
    }
}
