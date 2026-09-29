package com.nathzramirez.thesisflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

fun chapter(
    status: ChapterStatus,
    id: String = "ch-1",
    deadline: Instant? = null,
) = Chapter(
    id = id,
    groupId = "group-1",
    order = 1,
    title = "Chapter 1: Introduction",
    status = status,
    deadline = deadline,
    latestVersion = 0,
    updatedAt = null,
    hasPendingWrites = false,
)

class ThesisProgressTest {

    @Test
    fun `progress averages the status weights`() {
        val progress = ThesisProgress.of(
            listOf(
                chapter(ChapterStatus.APPROVED),
                chapter(ChapterStatus.APPROVED),
                chapter(ChapterStatus.FOR_REVIEW),
                chapter(ChapterStatus.DRAFTING),
                chapter(ChapterStatus.NOT_STARTED),
            ),
        )
        // (100 + 100 + 50 + 25 + 0) / 5 = 55
        assertEquals(ThesisProgress(percent = 55, approvedCount = 2, chapterCount = 5), progress)
    }

    @Test
    fun `a group without chapters is at zero, not a division by zero`() {
        assertEquals(ThesisProgress(0, 0, 0), ThesisProgress.of(emptyList()))
    }
}

class ChapterOverdueTest {
    private val deadline = Instant.parse("2026-10-01T15:59:59Z")

    @Test
    fun `a chapter past its deadline is overdue until approved`() {
        val after = deadline.plusSeconds(60)
        assertTrue(chapter(ChapterStatus.DRAFTING, deadline = deadline).isOverdue(after))
        assertFalse(chapter(ChapterStatus.APPROVED, deadline = deadline).isOverdue(after))
        assertFalse(chapter(ChapterStatus.DRAFTING, deadline = deadline).isOverdue(deadline.minusSeconds(60)))
        assertFalse(chapter(ChapterStatus.DRAFTING, deadline = null).isOverdue(after))
    }
}

class ChapterRulesTest {

    @Test
    fun `members can move a chapter anywhere except approved`() {
        val allowed = ChapterRules.allowedStatuses(Role.MEMBER, ChapterStatus.DRAFTING)
        assertEquals(
            listOf(ChapterStatus.NOT_STARTED, ChapterStatus.FOR_REVIEW, ChapterStatus.REVISIONS),
            allowed,
        )
    }

    @Test
    fun `members cannot undo an approval`() {
        assertEquals(emptyList<ChapterStatus>(), ChapterRules.allowedStatuses(Role.MEMBER, ChapterStatus.APPROVED))
    }

    @Test
    fun `leaders and advisers can approve and unapprove`() {
        for (role in listOf(Role.LEADER, Role.ADVISER)) {
            assertTrue(ChapterStatus.APPROVED in ChapterRules.allowedStatuses(role, ChapterStatus.FOR_REVIEW))
            assertTrue(ChapterStatus.REVISIONS in ChapterRules.allowedStatuses(role, ChapterStatus.APPROVED))
        }
    }

    @Test
    fun `only students upload drafts and only leaders manage chapters`() {
        assertTrue(ChapterRules.canUploadDrafts(Role.MEMBER))
        assertFalse(ChapterRules.canUploadDrafts(Role.ADVISER))
        assertTrue(ChapterRules.canManageChapters(Role.LEADER))
        assertFalse(ChapterRules.canManageChapters(Role.MEMBER))
        assertFalse(ChapterRules.canManageChapters(Role.ADVISER))
    }
}
