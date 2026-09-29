package com.nathzramirez.thesisflow.feature.chapters

import app.cash.turbine.test
import com.nathzramirez.thesisflow.MainDispatcherRule
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Group
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.chapter.ChangeChapterStatusUseCase
import com.nathzramirez.thesisflow.domain.usecase.file.QueueUploadUseCase
import com.nathzramirez.thesisflow.domain.validation.UploadRules
import com.nathzramirez.thesisflow.feature.chapters.detail.ChapterDetailViewModel
import com.nathzramirez.thesisflow.navigation.ChapterDetailRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChapterDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chapter = MutableStateFlow<Chapter?>(
        Chapter(
            id = "ch-1", groupId = "g-1", order = 1, title = "Chapter 1: Introduction",
            status = ChapterStatus.DRAFTING, deadline = null, latestVersion = 0,
            updatedAt = null, hasPendingWrites = false,
        ),
    )
    private val groups = mockk<GroupRepository> {
        every { observeGroup("g-1") } returns flowOf(
            Group("g-1", "Group 3", "", "BSCS", "UPang", Role.MEMBER, 3, null, null, null),
        )
    }
    private val chapters = mockk<ChapterRepository> {
        every { observeChapter("ch-1") } returns chapter
        every { observeVersions("ch-1") } returns flowOf(emptyList())
    }
    private val files = mockk<FileRepository> {
        every { observePendingForChapter("ch-1") } returns flowOf(emptyList())
        coEvery { queueUpload(any(), any(), any(), any()) } returns AppResult.Success(Unit)
    }

    private fun viewModel() = ChapterDetailViewModel(
        route = ChapterDetailRoute(groupId = "g-1", chapterId = "ch-1", number = 1),
        groupRepository = groups,
        chapterRepository = chapters,
        fileRepository = files,
        changeStatus = ChangeChapterStatusUseCase(groups, chapters),
        queueUpload = QueueUploadUseCase(groups, files),
    )

    @Test
    fun `a member is offered every status except approved`() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(
                listOf(ChapterStatus.NOT_STARTED, ChapterStatus.FOR_REVIEW, ChapterStatus.REVISIONS),
                state.allowedStatuses,
            )
            assertTrue(state.canUpload)
        }
    }

    @Test
    fun `an oversized file is refused before the note dialog opens`() = runTest {
        coEvery { files.inspect("content://big") } returns
            AppResult.Success(LocalFileInfo("scan.pdf", UploadRules.PDF, UploadRules.MAX_BYTES + 1))
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onFilePicked("content://big")
            val state = expectMostRecentItem()
            assertEquals(DomainError.FileTooLarge, state.error)
            assertNull(state.draft)
        }
    }

    @Test
    fun `a picked pdf is queued with its note`() = runTest {
        val pdf = LocalFileInfo("Chapter1_v2.pdf", UploadRules.PDF, 250_000)
        coEvery { files.inspect("content://draft") } returns AppResult.Success(pdf)
        val viewModel = viewModel()

        viewModel.uiState.test {
            viewModel.onFilePicked("content://draft")
            viewModel.onNoteChange("Fixed citations")
            viewModel.queueDraft()
            assertNull(expectMostRecentItem().draft)
        }
        coVerify {
            files.queueUpload("g-1", UploadTarget.ChapterDraft("ch-1", "Fixed citations"), "content://draft", pdf)
        }
    }

    @Test
    fun `deleting the chapter while it is open closes the screen`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            expectMostRecentItem()
            chapter.value = null
            assertTrue(awaitItem().isClosed)
        }
    }
}
