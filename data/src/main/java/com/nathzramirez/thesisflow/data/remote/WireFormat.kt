package com.nathzramirez.thesisflow.data.remote

import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.FileKind
import com.nathzramirez.thesisflow.domain.model.Role

/*
 * Enums are stored in Firestore as fixed lowercase strings, never as Kotlin's
 * enum names, so renaming a constant in code can't break documents already saved.
 * firestore.rules and the Cloud Functions use the same strings.
 */

internal fun Role.toWire(): String = when (this) {
    Role.LEADER -> "leader"
    Role.MEMBER -> "member"
    Role.ADVISER -> "adviser"
}

internal fun roleFromWire(value: String?): Role? = when (value) {
    "leader" -> Role.LEADER
    "member" -> Role.MEMBER
    "adviser" -> Role.ADVISER
    else -> null
}

internal fun ChapterStatus.toWire(): String = when (this) {
    ChapterStatus.NOT_STARTED -> "not_started"
    ChapterStatus.DRAFTING -> "drafting"
    ChapterStatus.FOR_REVIEW -> "for_review"
    ChapterStatus.REVISIONS -> "revisions"
    ChapterStatus.APPROVED -> "approved"
}

internal fun chapterStatusFromWire(value: String?): ChapterStatus? = when (value) {
    "not_started" -> ChapterStatus.NOT_STARTED
    "drafting" -> ChapterStatus.DRAFTING
    "for_review" -> ChapterStatus.FOR_REVIEW
    "revisions" -> ChapterStatus.REVISIONS
    "approved" -> ChapterStatus.APPROVED
    else -> null
}

internal fun FileKind.toWire(): String = when (this) {
    FileKind.DRAFT -> "draft"
    FileKind.ATTACHMENT -> "attachment"
    FileKind.FEEDBACK -> "feedback"
}

internal fun fileKindFromWire(value: String?): FileKind? = when (value) {
    "draft" -> FileKind.DRAFT
    "attachment" -> FileKind.ATTACHMENT
    "feedback" -> FileKind.FEEDBACK
    else -> null
}
