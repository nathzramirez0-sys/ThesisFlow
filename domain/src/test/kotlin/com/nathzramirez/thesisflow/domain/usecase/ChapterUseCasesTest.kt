package com.nathzramirez.thesisflow.domain.usecase

import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.chapter
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.chapter.AddChapterUseCase
import com.nathzramirez.thesisflow.domain.usecase.chapter.ChangeChapterStatusUseCase
import com.nathzramirez.thesisflow.domain.usecase.chapter.QueueDraftUploadUseCase
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
import org.junit.Test

class ChangeChapterStatusUseCaseTest {

    private val groups = mockk<GroupRepository>()
    private val chapters = mockk<ChapterRepository> {
        coEvery { setStatus(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val changeStatus = ChangeChapterStatusUseCase(groups, chapters)

    private fun given(role: Role, current: ChapterStatus) {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(role, memberCount = 3))
        every { chapters.observeChapter("ch-1") } returns flowOf(chapter(current))
    }

    @Test
    fun `a member submitting for review goes through`() = runTest {
        given(Role.MEMBER, ChapterStatus.DRAFTING)

        assertEquals(AppResult.Success(Unit), changeStatus(GROUP_ID, "ch-1", ChapterStatus.FOR_REVIEW))
        coVerify { chapters.setStatus(GROUP_ID, "ch-1", ChapterStatus.FOR_REVIEW) }
    }

    @Test
    fun `a member approving is refused before any write`() = runTest {
        given(Role.MEMBER, ChapterStatus.FOR_REVIEW)

        assertEquals(
            AppResult.Failure(DomainError.PermissionDenied),
            changeStatus(GROUP_ID, "ch-1", ChapterStatus.APPROVED),
        )
        coVerify(exactly = 0) { chapters.setStatus(any(), any(), any()) }
    }

    @Test
    fun `the adviser can approve`() = runTest {
        given(Role.ADVISER, ChapterStatus.FOR_REVIEW)

        assertEquals(AppResult.Success(Unit), changeStatus(GROUP_ID, "ch-1", ChapterStatus.APPROVED))
    }
}

class AddChapterUseCaseTest {

    private val chapters = mockk<ChapterRepository> {
        coEvery { addChapter(any(), any()) } returns AppResult.Success("ch-6")
    }
    private val addChapter = AddChapterUseCase(chapters)

    @Test
    fun `a blank title is rejected`() = runTest {
        assertEquals(
            AppResult.Failure(DomainError.InvalidInput(mapOf(Field.CHAPTER_TITLE to ValidationError.REQUIRED))),
            addChapter(GROUP_ID, "   "),
        )
    }

    @Test
    fun `the title is trimmed`() = runTest {
        assertEquals(AppResult.Success("ch-6"), addChapter(GROUP_ID, "  Appendices "))
        coVerify { chapters.addChapter(GROUP_ID, "Appendices") }
    }
}

class QueueDraftUploadUseCaseTest {

    private val groups = mockk<GroupRepository>()
    private val files = mockk<FileRepository> {
        coEvery { queueDraft(any(), any(), any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val queueDraft = QueueDraftUploadUseCase(groups, files)

    private fun given(role: Role, picked: LocalFileInfo) {
        every { groups.observeGroup(GROUP_ID) } returns flowOf(group(role, memberCount = 3))
        coEvery { files.inspect("content://draft") } returns AppResult.Success(picked)
    }

    @Test
    fun `a valid pdf is queued with its note trimmed`() = runTest {
        val pdf = LocalFileInfo("Chapter1_v2.pdf", UploadRules.PDF, 250_000)
        given(Role.MEMBER, pdf)

        assertEquals(AppResult.Success(Unit), queueDraft(GROUP_ID, "ch-1", "content://draft", " Fixed citations "))
        coVerify { files.queueDraft(GROUP_ID, "ch-1", "content://draft", pdf, "Fixed citations") }
    }

    @Test
    fun `an oversized file is rejected before copying`() = runTest {
        given(Role.MEMBER, LocalFileInfo("scan.pdf", UploadRules.PDF, UploadRules.MAX_BYTES + 1))

        assertEquals(AppResult.Failure(DomainError.FileTooLarge), queueDraft(GROUP_ID, "ch-1", "content://draft", ""))
        coVerify(exactly = 0) { files.queueDraft(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `advisers cannot upload drafts`() = runTest {
        given(Role.ADVISER, LocalFileInfo("notes.pdf", UploadRules.PDF, 1_000))

        assertEquals(
            AppResult.Failure(DomainError.PermissionDenied),
            queueDraft(GROUP_ID, "ch-1", "content://draft", ""),
        )
        coVerify(exactly = 0) { files.inspect(any()) }
    }
}
