package com.nathzramirez.thesisflow.domain.usecase.chapter

import com.nathzramirez.thesisflow.domain.model.ChapterRules
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.UploadRules
import com.nathzramirez.thesisflow.domain.validation.Validators
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Checks a picked file before anything is copied: role, type, size and note.
 * Rejecting a 50 MB video here beats discovering it after a failed upload.
 */
class QueueDraftUploadUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        chapterId: String,
        sourceUri: String,
        note: String,
    ): AppResult<Unit> {
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        if (!ChapterRules.canUploadDrafts(role)) return AppResult.Failure(DomainError.PermissionDenied)

        Validators.versionNote(note)?.let { error ->
            return AppResult.Failure(DomainError.InvalidInput(mapOf(Field.VERSION_NOTE to error)))
        }

        val file = when (val inspected = fileRepository.inspect(sourceUri)) {
            is AppResult.Success -> inspected.value
            is AppResult.Failure -> return inspected
        }
        UploadRules.check(file)?.let { return AppResult.Failure(it) }

        return fileRepository.queueDraft(groupId, chapterId, sourceUri, file, note.trim())
    }
}
