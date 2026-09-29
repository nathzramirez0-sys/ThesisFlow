package com.nathzramirez.thesisflow.domain.usecase.chapter

import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.domain.validation.Validators
import javax.inject.Inject

class AddChapterUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository,
) {
    /** Returns the new chapter's id. */
    suspend operator fun invoke(groupId: String, title: String): AppResult<String> {
        Validators.chapterTitle(title)?.let { return invalidTitle(it) }
        return chapterRepository.addChapter(groupId, title.trim())
    }
}

class RenameChapterUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository,
) {
    suspend operator fun invoke(groupId: String, chapterId: String, title: String): AppResult<Unit> {
        Validators.chapterTitle(title)?.let { return invalidTitle(it) }
        return chapterRepository.renameChapter(groupId, chapterId, title.trim())
    }
}

private fun invalidTitle(error: ValidationError) =
    AppResult.Failure(DomainError.InvalidInput(mapOf(Field.CHAPTER_TITLE to error)))
