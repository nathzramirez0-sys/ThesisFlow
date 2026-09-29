package com.nathzramirez.thesisflow.feature.chapters.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterRules
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.ChapterVersion
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.chapter.ChangeChapterStatusUseCase
import com.nathzramirez.thesisflow.domain.usecase.chapter.QueueDraftUploadUseCase
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** A picked file waiting for its note before it's queued. */
data class DraftToUpload(
    val sourceUri: String,
    val file: LocalFileInfo,
    val note: String = "",
    val noteError: ValidationError? = null,
    val isQueuing: Boolean = false,
)

/** A downloaded file the screen should hand to another app. */
data class FileToOpen(val path: String, val mimeType: String)

data class ChapterDetailUiState(
    val isLoading: Boolean = true,
    val chapter: Chapter? = null,
    val role: Role? = null,
    val versions: List<ChapterVersion> = emptyList(),
    val uploads: List<PendingUpload> = emptyList(),
    val isStatusPickerOpen: Boolean = false,
    val isDeadlinePickerOpen: Boolean = false,
    val draft: DraftToUpload? = null,
    val openingFileId: String? = null,
    val fileToOpen: FileToOpen? = null,
    val error: DomainError? = null,
    val isClosed: Boolean = false,
) {
    val allowedStatuses: List<ChapterStatus>
        get() = if (role == null || chapter == null) emptyList() else ChapterRules.allowedStatuses(role, chapter.status)
    val canManage: Boolean get() = role?.let(ChapterRules::canManageChapters) == true
    val canUpload: Boolean get() = role?.let(ChapterRules::canUploadDrafts) == true
}

private data class LocalState(
    val isStatusPickerOpen: Boolean = false,
    val isDeadlinePickerOpen: Boolean = false,
    val draft: DraftToUpload? = null,
    val openingFileId: String? = null,
    val fileToOpen: FileToOpen? = null,
    val error: DomainError? = null,
)

/**
 * Receives its route through assisted injection instead of reading SavedStateHandle,
 * so tests can pass a plain [ChapterDetailRoute].
 */
@HiltViewModel(assistedFactory = ChapterDetailViewModel.Factory::class)
class ChapterDetailViewModel @AssistedInject constructor(
    @Assisted private val route: ChapterDetailRoute,
    groupRepository: GroupRepository,
    private val chapterRepository: ChapterRepository,
    private val fileRepository: FileRepository,
    private val changeStatus: ChangeChapterStatusUseCase,
    private val queueDraftUpload: QueueDraftUploadUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ChapterDetailRoute): ChapterDetailViewModel
    }

    private val local = MutableStateFlow(LocalState())

    /** Distinguishes "still loading" from "deleted while open". */
    private var hasSeenChapter = false

    val uiState: StateFlow<ChapterDetailUiState> = combine(
        groupRepository.observeGroup(route.groupId),
        chapterRepository.observeChapter(route.chapterId),
        chapterRepository.observeVersions(route.chapterId),
        fileRepository.observePendingUploads(route.chapterId),
        local,
    ) { group, chapter, versions, uploads, local ->
        if (chapter != null) hasSeenChapter = true
        ChapterDetailUiState(
            isLoading = chapter == null && !hasSeenChapter,
            chapter = chapter,
            role = group?.myRole,
            versions = versions,
            uploads = uploads,
            isStatusPickerOpen = local.isStatusPickerOpen,
            isDeadlinePickerOpen = local.isDeadlinePickerOpen,
            draft = local.draft,
            openingFileId = local.openingFileId,
            fileToOpen = local.fileToOpen,
            error = local.error,
            isClosed = chapter == null && hasSeenChapter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterDetailUiState())

    fun openStatusPicker() = local.update { it.copy(isStatusPickerOpen = true) }

    fun dismissStatusPicker() = local.update { it.copy(isStatusPickerOpen = false) }

    fun changeStatus(status: ChapterStatus) {
        local.update { it.copy(isStatusPickerOpen = false) }
        viewModelScope.launch {
            val result = changeStatus(route.groupId, route.chapterId, status)
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }

    fun openDeadlinePicker() = local.update { it.copy(isDeadlinePickerOpen = true) }

    fun dismissDeadlinePicker() = local.update { it.copy(isDeadlinePickerOpen = false) }

    fun setDeadline(deadline: Instant?) {
        local.update { it.copy(isDeadlinePickerOpen = false) }
        viewModelScope.launch {
            val result = chapterRepository.setDeadline(route.groupId, route.chapterId, deadline)
            if (result is AppResult.Failure) local.update { it.copy(error = result.error) }
        }
    }

    /** Reads the picked file first, so a wrong type or size is reported before the note dialog. */
    fun onFilePicked(sourceUri: String) {
        viewModelScope.launch {
            when (val inspected = fileRepository.inspect(sourceUri)) {
                is AppResult.Failure -> local.update { it.copy(error = inspected.error) }
                is AppResult.Success -> {
                    val problem = UploadRules.check(inspected.value)
                    local.update {
                        if (problem != null) it.copy(error = problem)
                        else it.copy(draft = DraftToUpload(sourceUri, inspected.value))
                    }
                }
            }
        }
    }

    fun onNoteChange(value: String) = local.update {
        it.copy(draft = it.draft?.copy(note = value, noteError = null))
    }

    fun dismissDraft() = local.update { it.copy(draft = null) }

    fun queueDraft() {
        val draft = local.value.draft ?: return
        if (draft.isQueuing) return
        local.update { it.copy(draft = draft.copy(isQueuing = true)) }
        viewModelScope.launch {
            val result = queueDraftUpload(route.groupId, route.chapterId, draft.sourceUri, draft.note)
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(draft = null)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput ->
                            it.copy(draft = draft.copy(isQueuing = false, noteError = error.fieldErrors[Field.VERSION_NOTE]))
                        else -> it.copy(draft = null, error = error)
                    }
                }
            }
        }
    }

    fun retryUpload(fileId: String) = viewModelScope.launch { fileRepository.retryUpload(fileId) }

    fun discardUpload(fileId: String) = viewModelScope.launch { fileRepository.discardUpload(fileId) }

    /** Downloads the file if it isn't cached yet, then asks the screen to open it. */
    fun openFile(file: FileAttachment) {
        if (local.value.openingFileId != null) return
        local.update { it.copy(openingFileId = file.id) }
        viewModelScope.launch {
            val result = fileRepository.localCopy(file)
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(openingFileId = null, fileToOpen = FileToOpen(result.value, file.mimeType))
                    is AppResult.Failure -> it.copy(openingFileId = null, error = result.error)
                }
            }
        }
    }

    fun fileOpened() = local.update { it.copy(fileToOpen = null) }

    fun errorShown() = local.update { it.copy(error = null) }
}
