package com.nathzramirez.thesisflow.feature.chapters.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.Feedback
import com.nathzramirez.thesisflow.domain.model.FeedbackDraft
import com.nathzramirez.thesisflow.domain.model.FeedbackRules
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.UploadTarget
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.feedback.DeleteFeedbackUseCase
import com.nathzramirez.thesisflow.domain.usecase.feedback.PostFeedbackUseCase
import com.nathzramirez.thesisflow.domain.usecase.feedback.SetFeedbackResolvedUseCase
import com.nathzramirez.thesisflow.domain.usecase.file.QueueUploadUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.UploadRules
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.navigation.ChapterDetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedbackFilter { OPEN, RESOLVED }

/** A file picked on the phone, checked but not copied yet. */
data class PickedFile(val sourceUri: String, val file: LocalFileInfo)

/** The feedback composer sheet. */
data class FeedbackComposerState(
    val body: String = "",
    /** Which draft it is about; null for the chapter as a whole. */
    val versionNumber: Int?,
    val requestRevisions: Boolean,
    val attachment: PickedFile? = null,
    val bodyError: ValidationError? = null,
    val isPosting: Boolean = false,
)

/** One card: the feedback, its files still uploading, and what this user may do with it. */
data class FeedbackItem(
    val feedback: Feedback,
    val uploads: List<PendingUpload>,
    val canResolve: Boolean,
    val canReopen: Boolean,
    val canDelete: Boolean,
    val canAttach: Boolean,
)

/** Things to tell the user that aren't errors. */
enum class FeedbackNotice { POSTED, POSTED_WITHOUT_FILE }

data class ChapterFeedbackUiState(
    val items: List<FeedbackItem> = emptyList(),
    val openCount: Int = 0,
    val resolvedCount: Int = 0,
    val filter: FeedbackFilter = FeedbackFilter.OPEN,
    val canGiveFeedback: Boolean = false,
    /** Version numbers that exist, newest first, for the composer's picker. */
    val versions: List<Int> = emptyList(),
    /** False once the chapter is already in Revisions, so the switch would do nothing. */
    val canRequestRevisions: Boolean = false,
    val composer: FeedbackComposerState? = null,
    val confirmDelete: Feedback? = null,
    val notice: FeedbackNotice? = null,
    val error: DomainError? = null,
)

private data class FeedbackData(
    val uid: String?,
    val role: Role?,
    val chapter: Chapter?,
    val feedback: List<Feedback>,
    val uploads: List<PendingUpload>,
)

private data class FeedbackLocalState(
    val filter: FeedbackFilter = FeedbackFilter.OPEN,
    val composer: FeedbackComposerState? = null,
    val confirmDelete: Feedback? = null,
    /** The feedback a file is being picked for, kept here so it survives the picker. */
    val attachingTo: String? = null,
    val notice: FeedbackNotice? = null,
    val error: DomainError? = null,
)

/**
 * The Feedback section of chapter detail. It has its own ViewModel so the
 * chapter screen's ViewModel stays about drafts and status; both receive the
 * same route through assisted injection.
 */
@HiltViewModel(assistedFactory = ChapterFeedbackViewModel.Factory::class)
class ChapterFeedbackViewModel @AssistedInject constructor(
    @Assisted private val route: ChapterDetailRoute,
    authRepository: AuthRepository,
    groupRepository: GroupRepository,
    chapterRepository: ChapterRepository,
    feedbackRepository: FeedbackRepository,
    private val fileRepository: FileRepository,
    private val postFeedback: PostFeedbackUseCase,
    private val setResolved: SetFeedbackResolvedUseCase,
    private val deleteFeedback: DeleteFeedbackUseCase,
    private val queueUpload: QueueUploadUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ChapterDetailRoute): ChapterFeedbackViewModel
    }

    private val local = MutableStateFlow(FeedbackLocalState())

    private val data = combine(
        authRepository.authState.map { (it as? AuthState.SignedIn)?.uid },
        groupRepository.observeGroup(route.groupId).map { it?.myRole },
        chapterRepository.observeChapter(route.chapterId),
        feedbackRepository.observeFeedback(route.chapterId),
        fileRepository.observePendingFeedbackFiles(route.chapterId),
        ::FeedbackData,
    )

    val uiState: StateFlow<ChapterFeedbackUiState> = combine(data, local) { data, local ->
        val open = data.feedback.filter { !it.resolved }
        val resolved = data.feedback.filter { it.resolved }
        // An empty Resolved tab (its last item reopened, maybe by someone else) falls back to Open.
        val filter = if (local.filter == FeedbackFilter.RESOLVED && resolved.isEmpty()) FeedbackFilter.OPEN else local.filter
        val shown = if (filter == FeedbackFilter.OPEN) open else resolved
        val uploadsByFeedback = data.uploads.groupBy { it.feedbackId }
        ChapterFeedbackUiState(
            items = shown.map { feedback -> feedback.toItem(data, uploadsByFeedback[feedback.id].orEmpty()) },
            openCount = open.size,
            resolvedCount = resolved.size,
            filter = filter,
            canGiveFeedback = data.role?.let(FeedbackRules::canGiveFeedback) == true,
            versions = data.chapter?.latestVersion?.let { latest -> (latest downTo 1).toList() }.orEmpty(),
            canRequestRevisions = data.chapter != null && data.chapter.status != ChapterStatus.REVISIONS,
            composer = local.composer,
            confirmDelete = local.confirmDelete,
            notice = local.notice,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterFeedbackUiState())

    private fun Feedback.toItem(data: FeedbackData, uploads: List<PendingUpload>): FeedbackItem {
        val uid = data.uid
        val role = data.role
        return FeedbackItem(
            feedback = this,
            uploads = uploads,
            canResolve = role != null && FeedbackRules.canResolve(this),
            canReopen = role != null && uid != null && FeedbackRules.canReopen(role, this, uid),
            canDelete = uid != null && FeedbackRules.canDelete(this, uid),
            canAttach = role != null && uid != null && FeedbackRules.canAttachFiles(role, this, uid),
        )
    }

    fun setFilter(filter: FeedbackFilter) = local.update { it.copy(filter = filter) }

    /**
     * Starts on the newest draft, since that is what an adviser usually reviews.
     * Asking for revisions changes the chapter's status, so it is never on by default.
     */
    fun openComposer() {
        val state = uiState.value
        local.update {
            it.copy(
                composer = FeedbackComposerState(
                    versionNumber = state.versions.firstOrNull(),
                    requestRevisions = false,
                ),
            )
        }
    }

    fun dismissComposer() = local.update { if (it.composer?.isPosting == true) it else it.copy(composer = null) }

    fun onBodyChange(value: String) = updateComposer { it.copy(body = value, bodyError = null) }

    fun onVersionChange(versionNumber: Int?) = updateComposer { it.copy(versionNumber = versionNumber) }

    fun onRequestRevisionsChange(value: Boolean) = updateComposer { it.copy(requestRevisions = value) }

    /** Checks the file straight away, so a wrong type or size shows before posting. */
    fun onComposerFilePicked(sourceUri: String) {
        viewModelScope.launch {
            when (val checked = check(sourceUri)) {
                is AppResult.Success -> updateComposer { it.copy(attachment = checked.value) }
                is AppResult.Failure -> local.update { it.copy(error = checked.error) }
            }
        }
    }

    fun removeComposerFile() = updateComposer { it.copy(attachment = null) }

    fun post() {
        val composer = local.value.composer ?: return
        if (composer.isPosting) return
        updateComposer { it.copy(isPosting = true) }
        viewModelScope.launch {
            val draft = FeedbackDraft(composer.body, composer.versionNumber, composer.requestRevisions)
            val result = postFeedback(route.groupId, route.chapterId, draft, composer.attachment?.sourceUri)
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        composer = null,
                        filter = FeedbackFilter.OPEN,
                        notice = if (result.value.attachmentError == null) {
                            FeedbackNotice.POSTED
                        } else {
                            FeedbackNotice.POSTED_WITHOUT_FILE
                        },
                    )
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput -> it.copy(
                            composer = composer.copy(isPosting = false, bodyError = error.fieldErrors[Field.FEEDBACK]),
                        )
                        else -> it.copy(composer = composer.copy(isPosting = false), error = error)
                    }
                }
            }
        }
    }

    fun setResolved(feedbackId: String, resolved: Boolean) {
        // Reopening the last resolved item: follow it to the Open tab, and stay there later.
        if (!resolved && uiState.value.resolvedCount <= 1) local.update { it.copy(filter = FeedbackFilter.OPEN) }
        launchReportingErrors { setResolved(route.groupId, feedbackId, resolved) }
    }

    fun askDelete(feedback: Feedback) = local.update { it.copy(confirmDelete = feedback) }

    fun dismissDelete() = local.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val feedback = local.value.confirmDelete ?: return
        local.update { it.copy(confirmDelete = null) }
        launchReportingErrors { deleteFeedback(route.groupId, feedback.id) }
    }

    /** Remembers which feedback the picker is for; the screen then opens the picker. */
    fun startAttaching(feedbackId: String) = local.update { it.copy(attachingTo = feedbackId) }

    fun onAttachmentPicked(sourceUri: String?) {
        val feedbackId = local.value.attachingTo ?: return
        local.update { it.copy(attachingTo = null) }
        if (sourceUri == null) return
        launchReportingErrors {
            queueUpload(route.groupId, UploadTarget.FeedbackFile(route.chapterId, feedbackId), sourceUri)
        }
    }

    fun retryUpload(fileId: String) = viewModelScope.launch { fileRepository.retryUpload(fileId) }

    fun discardUpload(fileId: String) = viewModelScope.launch { fileRepository.discardUpload(fileId) }

    fun noticeShown() = local.update { it.copy(notice = null) }

    fun errorShown() = local.update { it.copy(error = null) }

    private suspend fun check(sourceUri: String): AppResult<PickedFile> =
        when (val inspected = fileRepository.inspect(sourceUri)) {
            is AppResult.Failure -> inspected
            is AppResult.Success -> UploadRules.check(inspected.value)
                ?.let { AppResult.Failure(it) }
                ?: AppResult.Success(PickedFile(sourceUri, inspected.value))
        }

    private fun updateComposer(change: (FeedbackComposerState) -> FeedbackComposerState) =
        local.update { state -> state.composer?.let { state.copy(composer = change(it)) } ?: state }

    private fun launchReportingErrors(action: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            val result = action()
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }
}
