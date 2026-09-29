package com.nathzramirez.thesisflow.domain.model

/**
 * Who may do what with chapters. firestore.rules and storage.rules enforce the
 * same table on the server; this copy lets the UI offer only allowed actions.
 */
object ChapterRules {

    /** Adding, renaming and deleting chapters, and setting deadlines. */
    fun canManageChapters(role: Role): Boolean = role == Role.LEADER

    /** Advisers review drafts; their own files go on feedback instead. */
    fun canUploadDrafts(role: Role): Boolean = role != Role.ADVISER

    /**
     * Statuses [role] may move a chapter to from [current]. Only leaders and
     * advisers can approve a chapter, or undo an approval.
     */
    fun allowedStatuses(role: Role, current: ChapterStatus): List<ChapterStatus> {
        if (role == Role.MEMBER && current == ChapterStatus.APPROVED) return emptyList()
        return ChapterStatus.entries.filter { status ->
            status != current && (role != Role.MEMBER || status != ChapterStatus.APPROVED)
        }
    }
}
