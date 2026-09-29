package com.nathzramirez.thesisflow.feature.chapters

import app.cash.turbine.test
import com.nathzramirez.thesisflow.MainDispatcherRule
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.usecase.feedback.DeleteFeedbackUseCase
import com.nathzramirez.thesisflow.domain.usecase.feedback.PostFeedbackUseCase
import com.nathzramirez.thesisflow.domain.usecase.feedback.SetFeedbackResolvedUseCase
import com.nathzramirez.thesisflow.domain.usecase.file.QueueUploadUseCase
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.feature.chapters.detail.ChapterFeedbackViewModel
import com.nathzramirez.thesisflow.feature.chapters.detail.FeedbackFilter
import com.nathzramirez.thesisflow.feature.chapters.detail.FeedbackNotice
import com.nathzramirez.thesisflow.navigation.ChapterDetailRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChapterFeedbackViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chapter = Chapter(
        id = "ch-1", groupId = "g-1", order = 1, title = "Introduction",
        status = ChapterStatus.FOR_REVIEW, deadline = null, latestVersion = 3,
        updatedAt = null, hasPendingWrites = false,
    )

    private fun feedback(id: String, resolved: Boolean) = Feedback(
        id = id, groupId = "g-1", chapterId = "ch-1", body = "Fix 1.2", authorId = "dr",
        authorName = "Dr. Reyes", authorRole = Role.ADVISER, versionNumber = 3, resolved = resolved,
        resolvedBy = if (resolved) "ben" else null, resolverName = null, resolvedAt = null,
        createdAt = null, hasPendingWrites = false, files = emptyList(),
    )

    private val chapters = mockk<ChapterRepository> { every { observeChapter("ch-1") } returns flowOf(chapter) }
    private val feedbackRepo = mockk<FeedbackRepository> {
        every { observeFeedback("ch-1") } returns flowOf(
            listOf(feedback("fb-1", resolved = false), feedback("fb-2", resolved = true), feedback("fb-3", resolved = false)),
        )
        coEvery { postFeedback(any(), any(), any(), any(), any()) } returns AppResult.Success("fb-9")
    }
    private val files = mockk<FileRepository> {
        every { observePendingFeedbackFiles("ch-1") } returns flowOf(emptyList())
    }

    private fun viewModel(uid: String, role: Role): ChapterFeedbackViewModel {
        val auth = mockk<AuthRepository> { every { authState } returns flowOf(AuthState.SignedIn(uid)) }
        val groups = mockk<GroupRepository> {
            every { observeGroup("g-1") } returns flowOf(Group("g-1", "Group 3", "", "BSCS", "UPang", role, 4, null, null, null))
        }
        return ChapterFeedbackViewModel(
            route = ChapterDetailRoute(groupId = "g-1", chapterId = "ch-1", number = 1),
            authRepository = auth,
            groupRepository = groups,
            chapterRepository = chapters,
            feedbackRepository = feedbackRepo,
            fileRepository = files,
            postFeedback = PostFeedbackUseCase(groups, chapters, feedbackRepo, files),
            setResolved = SetFeedbackResolvedUseCase(auth, groups, feedbackRepo),
            deleteFeedback = DeleteFeedbackUseCase(auth, feedbackRepo),
            queueUpload = QueueUploadUseCase(groups, files),
        )
    }

    @Test
    fun `students see open feedback first and may resolve but not delete it`() = runTest {
        viewModel(uid = "ben", role = Role.MEMBER).uiState.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.openCount)
            assertEquals(1, state.resolvedCount)
            assertEquals(listOf("fb-1", "fb-3"), state.items.map { it.feedback.id })
            assertTrue(state.items.all { it.canResolve && !it.canDelete && !it.canAttach })
            assertFalse(state.canGiveFeedback)
        }
    }

    @Test
    fun `the resolved tab lets whoever resolved it reopen it`() = runTest {
        val viewModel = viewModel(uid = "ben", role = Role.MEMBER)
        viewModel.uiState.test {
            viewModel.setFilter(FeedbackFilter.RESOLVED)
            val item = expectMostRecentItem().items.single()
            assertEquals("fb-2", item.feedback.id)
            assertTrue(item.canReopen)
        }
    }

    @Test
    fun `the composer starts on the newest draft with revisions off`() = runTest {
        val viewModel = viewModel(uid = "dr", role = Role.ADVISER)
        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.openComposer()
            val state = expectMostRecentItem()
            assertEquals(listOf(3, 2, 1), state.versions)
            assertEquals(3, state.composer?.versionNumber)
            assertEquals(false, state.composer?.requestRevisions)
            assertTrue(state.canGiveFeedback)
        }
    }

    @Test
    fun `an empty post keeps the composer open with the field error`() = runTest {
        val viewModel = viewModel(uid = "dr", role = Role.ADVISER)
        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.openComposer()
            viewModel.post()
            val state = expectMostRecentItem()
            assertEquals(ValidationError.REQUIRED, state.composer?.bodyError)
            coVerify(exactly = 0) { feedbackRepo.postFeedback(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `posting closes the composer and says so`() = runTest {
        val viewModel = viewModel(uid = "dr", role = Role.ADVISER)
        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.openComposer()
            viewModel.onBodyChange("Cite your sources in 1.2.")
            viewModel.onRequestRevisionsChange(true)
            viewModel.post()
            val state = expectMostRecentItem()
            assertNull(state.composer)
            assertEquals(FeedbackNotice.POSTED, state.notice)
            coVerify { feedbackRepo.postFeedback("g-1", "ch-1", "Cite your sources in 1.2.", 3, true) }
        }
    }
}
