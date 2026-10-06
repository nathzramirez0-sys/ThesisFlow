package com.nathzramirez.thesisflow.data.local

import com.nathzramirez.thesisflow.data.local.entity.ActivityEntity
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityMappingTest {

    private fun entry(type: String, targetId: String? = "ch-1", detail: String? = null) = ActivityEntity(
        id = "a-1",
        groupId = "group-1",
        type = type,
        actorId = "ben",
        actorName = "Ben Cruz",
        targetId = targetId,
        targetTitle = "Introduction",
        detail = detail,
        createdAt = null,
    )

    @Test
    fun `each wire type becomes its event`() {
        assertEquals(ActivityEvent.MemberJoined(Role.ADVISER), entry("member_joined", null, "adviser").toDomain()?.event)
        assertEquals(ActivityEvent.MemberLeft, entry("member_left", null).toDomain()?.event)
        assertEquals(
            ActivityEvent.ChapterStatusChanged("ch-1", "Introduction", ChapterStatus.FOR_REVIEW),
            entry("chapter_status", detail = "for_review").toDomain()?.event,
        )
        assertEquals(
            ActivityEvent.DraftUploaded("ch-1", "Introduction", 3),
            entry("draft_uploaded", detail = "3").toDomain()?.event,
        )
        assertEquals(
            ActivityEvent.FeedbackPosted("ch-1", "Introduction", null),
            entry("feedback_posted").toDomain()?.event,
        )
        assertEquals(
            ActivityEvent.TaskStatusChanged("ch-1", "Introduction", TaskStatus.DONE),
            entry("task_status", detail = "done").toDomain()?.event,
        )
        assertEquals(
            ActivityEvent.DefenseScheduled(DefenseKind.FINAL),
            entry("defense_scheduled", null, "final").toDomain()?.event,
        )
    }

    @Test
    fun `entries this version can't read are skipped, not crashed on`() {
        assertNull(entry("chapter_renamed").toDomain())
        assertNull(entry("chapter_status", detail = "archived").toDomain())
        assertNull(entry("draft_uploaded", detail = null).toDomain())
        assertNull(entry("task_created", targetId = null).toDomain())
    }
}
