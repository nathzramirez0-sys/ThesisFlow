package com.nathzramirez.thesisflow.domain.usecase.file

import com.nathzramirez.thesisflow.domain.model.ChapterRules
import com.nathzramirez.thesisflow.domain.model.TaskRules
import com.nathzramirez.thesisflow.domain.model.UploadTarget
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
 * Checks a picked file before anything is copied: role, type, size, and the note
 * for chapter drafts. Rejecting a 50 MB video here beats discovering it after a
 * failed upload. One use case serves every [UploadTarget].
 */
class QueueUploadUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(groupId: String, target: UploadTarget, sourceUri: String): AppResult<Unit> {
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        val allowed = when (target) {
            is UploadTarget.ChapterDraft -> ChapterRules.canUploadDrafts(role)
            is UploadTarget.TaskAttachment -> TaskRules.canAttachFiles(role)
        }
        if (!allowed) return AppResult.Failure(DomainError.PermissionDenied)

        val cleanTarget = when (target) {
            is UploadTarget.ChapterDraft -> {
                Validators.versionNote(target.note)?.let { error ->
                    return AppResult.Failure(DomainError.InvalidInput(mapOf(Field.VERSION_NOTE to error)))
                }
                target.copy(note = target.note.trim())
            }
            is UploadTarget.TaskAttachment -> target
        }

        val file = when (val inspected = fileRepository.inspect(sourceUri)) {
            is AppResult.Success -> inspected.value
            is AppResult.Failure -> return inspected
        }
        UploadRules.check(file)?.let { return AppResult.Failure(it) }

        return fileRepository.queueUpload(groupId, cleanTarget, sourceUri, file)
    }
}
