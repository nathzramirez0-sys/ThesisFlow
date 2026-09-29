package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

/**
 * The adviser's comments on a chapter. Each one stays open until a student marks
 * it resolved, so the group has a to-do list of revisions instead of a chat log.
 */
data class Feedback(
    val id: String,
    val groupId: String,
    val chapterId: String,
    val body: String,
    val authorId: String,
    /** Copied when posted, so the feedback reads right even after the author leaves. */
    val authorName: String,
    /** The author's role when they posted it. */
    val authorRole: Role,
    /** The draft it is about, or null for the chapter as a whole. */
    val versionNumber: Int?,
    val resolved: Boolean,
    val resolvedBy: String?,
    /** Null while open, or when the person who resolved it has since left the group. */
    val resolverName: String?,
    val resolvedAt: Instant?,
    val createdAt: Instant?,
    /** True while a local edit hasn't reached the server yet, e.g. offline. */
    val hasPendingWrites: Boolean,
    /** Files the author attached, e.g. a marked-up copy of the draft. */
    val files: List<FileAttachment>,
)

/** What the feedback composer produces. */
data class FeedbackDraft(
    val body: String,
    val versionNumber: Int?,
    /** Also move the chapter to Revisions, in the same write as the feedback. */
    val requestRevisions: Boolean,
)

/**
 * Who may do what with feedback. firestore.rules enforces the same table on the
 * server; this copy lets the UI offer only allowed actions.
 */
object FeedbackRules {

    /** Feedback is the adviser's voice; students answer it by revising the chapter. */
    fun canGiveFeedback(role: Role): Boolean = role == Role.ADVISER

    /** Anyone in the group can mark open feedback as addressed. */
    fun canResolve(feedback: Feedback): Boolean = !feedback.resolved

    /**
     * Advisers decide whether a fix was enough, so they can reopen anything.
     * Whoever resolved it can also undo a mistaken tap.
     */
    fun canReopen(role: Role, feedback: Feedback, uid: String): Boolean =
        feedback.resolved && (role == Role.ADVISER || feedback.resolvedBy == uid)

    /** Only the author can take feedback back; students can't delete what they were told. */
    fun canDelete(feedback: Feedback, uid: String): Boolean = feedback.authorId == uid

    /** Authors add files to their own feedback; storage.rules also requires the adviser role. */
    fun canAttachFiles(role: Role, feedback: Feedback, uid: String): Boolean =
        role == Role.ADVISER && feedback.authorId == uid
}
