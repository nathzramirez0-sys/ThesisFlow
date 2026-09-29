package com.nathzramirez.thesisflow.domain.model

/** Where an uploaded file belongs. Each target maps to one [FileKind]. */
sealed interface UploadTarget {
    val kind: FileKind

    /** A new numbered version of a chapter, with an optional "what changed" note. */
    data class ChapterDraft(val chapterId: String, val note: String) : UploadTarget {
        override val kind get() = FileKind.DRAFT
    }

    /** A supporting file on a task. */
    data class TaskAttachment(val taskId: String) : UploadTarget {
        override val kind get() = FileKind.ATTACHMENT
    }

    /** A file on the adviser's own feedback, e.g. a marked-up copy of the draft. */
    data class FeedbackFile(val chapterId: String, val feedbackId: String) : UploadTarget {
        override val kind get() = FileKind.FEEDBACK
    }
}
