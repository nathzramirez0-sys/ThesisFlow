package com.nathzramirez.thesisflow.domain.model

import kotlin.math.roundToInt

/**
 * Overall progress: the average of each chapter's status weight, so a thesis
 * with every chapter in review reads 50% and one with every chapter approved 100%.
 */
data class ThesisProgress(
    val percent: Int,
    val approvedCount: Int,
    val chapterCount: Int,
) {
    companion object {
        fun of(chapters: List<Chapter>): ThesisProgress {
            if (chapters.isEmpty()) return ThesisProgress(percent = 0, approvedCount = 0, chapterCount = 0)
            return ThesisProgress(
                percent = chapters.map { it.status.progressWeight }.average().roundToInt(),
                approvedCount = chapters.count { it.status == ChapterStatus.APPROVED },
                chapterCount = chapters.size,
            )
        }
    }
}
