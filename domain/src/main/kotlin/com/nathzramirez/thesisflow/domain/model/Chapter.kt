package com.nathzramirez.thesisflow.domain.model

import java.time.Instant

enum class ChapterStatus(
    /** How much a chapter in this status counts toward thesis progress, out of 100. */
    val progressWeight: Int,
) {
    NOT_STARTED(0),
    DRAFTING(25),
    FOR_REVIEW(50),
    REVISIONS(75),
    APPROVED(100),
}

data class Chapter(
    val id: String,
    val groupId: String,
    val order: Int,
    val title: String,
    val status: ChapterStatus,
    val deadline: Instant?,
    /** 0 until the first draft is uploaded. */
    val latestVersion: Int,
    val updatedAt: Instant?,
    /** True while a local edit hasn't reached the server yet, e.g. offline. */
    val hasPendingWrites: Boolean,
) {
    fun isOverdue(now: Instant): Boolean =
        deadline != null && status != ChapterStatus.APPROVED && now.isAfter(deadline)
}

/**
 * The five chapters most Philippine undergraduate theses follow; every new group
 * starts with these. The app shows the number ("Chapter 01"), so titles leave it out.
 */
object DefaultChapters {
    val titles = listOf(
        "Introduction",
        "Review of Related Literature",
        "Methodology",
        "Results and Discussion",
        "Summary, Conclusions and Recommendations",
    )
}
