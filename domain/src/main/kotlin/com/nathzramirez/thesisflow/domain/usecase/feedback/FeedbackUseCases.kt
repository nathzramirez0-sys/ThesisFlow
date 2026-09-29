package com.nathzramirez.thesisflow.domain.usecase.feedback

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.FeedbackDraft
import com.nathzramirez.thesisflow.domain.model.FeedbackRules
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
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
 * The feedback is posted. [attachmentError] is set when its file couldn't be
 * queued afterwards; the feedback stays, and the file can be attached again.
 */
data class FeedbackPosted(val feedbackId: String, val attachmentError: DomainError?)

/**
 * Posts adviser feedback on a chapter, optionally with a file and a request for
 * revisions.
 *
 * Everything that can be checked is checked before the first write, the file
 * included, so a rejected file never leaves feedback posted without it. Only a
 * failure after posting (the file vanished while it was being copied) is
 * reported through [FeedbackPosted.attachmentError] instead of as a failure,
 * so the adviser isn't invited to post the same feedback twice.
 */
class PostFeedbackUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val chapterRepository: ChapterRepository,
    private val feedbackRepository: FeedbackRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        chapterId: String,
        draft: FeedbackDraft,
        attachmentUri: String?,
    ): AppResult<FeedbackPosted> {
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        if (!FeedbackRules.canGiveFeedback(role)) return AppResult.Failure(DomainError.PermissionDenied)

        Validators.feedback(draft.body)?.let { error ->
            return AppResult.Failure(DomainError.InvalidInput(mapOf(Field.FEEDBACK to error)))
        }
        val chapter = chapterRepository.observeChapter(chapterId).first()
            ?: return AppResult.Failure(DomainError.NotFound)
        val version = draft.versionNumber
        if (version != null && version !in 1..chapter.latestVersion) return AppResult.Failure(DomainError.NotFound)

        val file: LocalFileInfo? = if (attachmentUri == null) {
            null
        } else {
            when (val inspected = fileRepository.inspect(attachmentUri)) {
                is AppResult.Success -> inspected.value
                is AppResult.Failure -> return inspected
            }
        }
        file?.let { UploadRules.check(it) }?.let { return AppResult.Failure(it) }

        val posted = feedbackRepository.postFeedback(
            groupId = groupId,
            chapterId = chapterId,
            body = draft.body.trim(),
            versionNumber = version,
            moveToRevisions = draft.requestRevisions && chapter.status != ChapterStatus.REVISIONS,
        )
        val feedbackId = when (posted) {
            is AppResult.Success -> posted.value
            is AppResult.Failure -> return posted
        }
        if (attachmentUri == null || file == null) return AppResult.Success(FeedbackPosted(feedbackId, null))

        val queued = fileRepository.queueUpload(
            groupId = groupId,
            target = UploadTarget.FeedbackFile(chapterId, feedbackId),
            sourceUri = attachmentUri,
            file = file,
        )
        return AppResult.Success(FeedbackPosted(feedbackId, (queued as? AppResult.Failure)?.error))
    }
}

/**
 * Marks feedback resolved, or reopens it, if the user may. Checking here gives
 * a clear message at once, even offline, where a rules rejection would only
 * arrive later and silently undo the change.
 */
class SetFeedbackResolvedUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val feedbackRepository: FeedbackRepository,
) {
    suspend operator fun invoke(groupId: String, feedbackId: String, resolved: Boolean): AppResult<Unit> {
        val uid = (authRepository.authState.first() as? AuthState.SignedIn)?.uid
            ?: return AppResult.Failure(DomainError.PermissionDenied)
        val role = groupRepository.observeGroup(groupId).first()?.myRole
            ?: return AppResult.Failure(DomainError.NotFound)
        val feedback = feedbackRepository.observeFeedbackById(feedbackId).first()
            ?: return AppResult.Failure(DomainError.NotFound)

        if (feedback.resolved == resolved) return AppResult.Success(Unit)
        val allowed = if (resolved) {
            FeedbackRules.canResolve(feedback)
        } else {
            FeedbackRules.canReopen(role, feedback, uid)
        }
        if (!allowed) return AppResult.Failure(DomainError.PermissionDenied)
        return feedbackRepository.setResolved(groupId, feedback.chapterId, feedbackId, resolved)
    }
}

/** Only the author may delete feedback; its files go with it. */
class DeleteFeedbackUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val feedbackRepository: FeedbackRepository,
) {
    suspend operator fun invoke(groupId: String, feedbackId: String): AppResult<Unit> {
        val uid = (authRepository.authState.first() as? AuthState.SignedIn)?.uid
            ?: return AppResult.Failure(DomainError.PermissionDenied)
        val feedback = feedbackRepository.observeFeedbackById(feedbackId).first()
            ?: return AppResult.Success(Unit) // Already gone.
        if (!FeedbackRules.canDelete(feedback, uid)) return AppResult.Failure(DomainError.PermissionDenied)
        return feedbackRepository.deleteFeedback(groupId, feedback.chapterId, feedbackId)
    }
}
