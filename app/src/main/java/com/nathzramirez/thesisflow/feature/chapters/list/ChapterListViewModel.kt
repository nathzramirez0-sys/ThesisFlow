package com.nathzramirez.thesisflow.feature.chapters.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.ChapterRules
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.ThesisProgress
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.usecase.chapter.AddChapterUseCase
import com.nathzramirez.thesisflow.domain.usecase.chapter.RenameChapterUseCase
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.navigation.ChapterListRoute
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

/** The add/rename dialog. [chapterId] is null when adding. */
data class TitleDialogState(
    val chapterId: String?,
    val title: String,
    val error: ValidationError? = null,
    val isSaving: Boolean = false,
)

data class ChapterListUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val role: Role? = null,
    val chapters: List<Chapter> = emptyList(),
    val progress: ThesisProgress = ThesisProgress(0, 0, 0),
    /** Open feedback per chapter id; chapters without any are missing. */
    val openFeedback: Map<String, Int> = emptyMap(),
    val titleDialog: TitleDialogState? = null,
    val confirmDelete: Chapter? = null,
    val isBusy: Boolean = false,
    val error: DomainError? = null,
) {
    val canManage: Boolean get() = role?.let(ChapterRules::canManageChapters) == true
}

/** What the user is doing on this screen, as opposed to the data it shows. */
private data class LocalState(
    val titleDialog: TitleDialogState? = null,
    val confirmDelete: Chapter? = null,
    val isBusy: Boolean = false,
    val error: DomainError? = null,
)

/**
 * Receives its route through assisted injection instead of reading SavedStateHandle,
 * so tests can pass a plain [ChapterListRoute].
 */
@HiltViewModel(assistedFactory = ChapterListViewModel.Factory::class)
class ChapterListViewModel @AssistedInject constructor(
    @Assisted private val route: ChapterListRoute,
    groupRepository: GroupRepository,
    private val chapterRepository: ChapterRepository,
    feedbackRepository: FeedbackRepository,
    private val addChapter: AddChapterUseCase,
    private val renameChapter: RenameChapterUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ChapterListRoute): ChapterListViewModel
    }

    private val groupId = route.groupId
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ChapterListUiState> = combine(
        groupRepository.observeGroup(groupId),
        chapterRepository.observeChapters(groupId),
        feedbackRepository.observeOpenCounts(groupId),
        local,
    ) { group, chapters, openFeedback, local ->
        ChapterListUiState(
            isLoading = group == null,
            groupName = group?.name.orEmpty(),
            role = group?.myRole,
            chapters = chapters,
            progress = ThesisProgress.of(chapters),
            openFeedback = openFeedback,
            titleDialog = local.titleDialog,
            confirmDelete = local.confirmDelete,
            isBusy = local.isBusy,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterListUiState())

    fun openAddDialog() = local.update { it.copy(titleDialog = TitleDialogState(chapterId = null, title = "")) }

    fun openRenameDialog(chapter: Chapter) =
        local.update { it.copy(titleDialog = TitleDialogState(chapterId = chapter.id, title = chapter.title)) }

    fun onTitleChange(value: String) = local.update {
        it.copy(titleDialog = it.titleDialog?.copy(title = value, error = null))
    }

    fun dismissTitleDialog() = local.update { it.copy(titleDialog = null) }

    fun saveTitle() {
        val dialog = local.value.titleDialog ?: return
        if (dialog.isSaving) return
        local.update { it.copy(titleDialog = dialog.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = if (dialog.chapterId == null) {
                addChapter(groupId, dialog.title)
            } else {
                renameChapter(groupId, dialog.chapterId, dialog.title)
            }
            local.update {
                when (result) {
                    is AppResult.Success -> it.copy(titleDialog = null)
                    is AppResult.Failure -> when (val error = result.error) {
                        is DomainError.InvalidInput -> it.copy(
                            titleDialog = dialog.copy(isSaving = false, error = error.fieldErrors[Field.CHAPTER_TITLE]),
                        )
                        else -> it.copy(titleDialog = dialog.copy(isSaving = false), error = error)
                    }
                }
            }
        }
    }

    fun askDelete(chapter: Chapter) = local.update { it.copy(confirmDelete = chapter) }

    fun dismissDelete() = local.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val chapter = local.value.confirmDelete ?: return
        local.update { it.copy(confirmDelete = null) }
        run { chapterRepository.deleteChapter(groupId, chapter.id) }
    }

    fun addDefaultChapters() = run { chapterRepository.addDefaultChapters(groupId) }

    fun errorShown() = local.update { it.copy(error = null) }

    private fun run(action: suspend () -> AppResult<*>) {
        if (local.value.isBusy) return
        local.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = action()
            local.update { it.copy(isBusy = false, error = (result as? AppResult.Failure)?.error) }
        }
    }
}
