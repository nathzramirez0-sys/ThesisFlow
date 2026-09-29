package com.nathzramirez.thesisflow.domain.usecase.chapter

import com.nathzramirez.thesisflow.domain.model.ChapterRules
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Moves a chapter to a new status if the user's role allows it. Checking here
 * gives a clear message at once, even offline, where a rules rejection would
 * only arrive later and silently undo the change.
 */
class ChangeChapterStatusUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val chapterRepository: ChapterRepository,
) {
    suspend operator fun invoke(groupId: String, chapterId: String, newStatus: ChapterStatus): AppResult<Unit> {
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        val chapter = chapterRepository.observeChapter(chapterId).first()
            ?: return AppResult.Failure(DomainError.NotFound)

        if (chapter.status == newStatus) return AppResult.Success(Unit)
        if (newStatus !in ChapterRules.allowedStatuses(role, chapter.status)) {
            return AppResult.Failure(DomainError.PermissionDenied)
        }
        return chapterRepository.setStatus(groupId, chapterId, newStatus)
    }
}
