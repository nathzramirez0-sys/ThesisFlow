package com.nathzramirez.thesisflow.data.push

import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.PushEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PushPayloadsTest {

    private fun payload(type: String, detail: String = "", targetId: String = "ch-1", vararg extra: Pair<String, String>) =
        mapOf(
            "recipientId" to "ben",
            "type" to type,
            "groupId" to "g-1",
            "groupName" to "Team SmartBin",
            "actorName" to "Prof. Liza Reyes",
            "targetId" to targetId,
            "targetTitle" to "Introduction",
            "detail" to detail,
        ) + extra

    @Test
    fun `feedback carries its preview and version`() {
        val notice = PushPayloads.parse(payload("feedback_posted", "2", "ch-1", "preview" to "Cite 1.2"))
        assertEquals(PushEvent.FeedbackPosted("ch-1", "Introduction", 2, "Cite 1.2"), notice?.event)
        assertEquals("ben", notice?.recipientId)
    }

    @Test
    fun `chapter statuses map to what the reader cares about`() {
        assertEquals(PushEvent.ReadyForReview("ch-1", "Introduction"), PushPayloads.parse(payload("chapter_status", "for_review"))?.event)
        assertEquals(PushEvent.RevisionsRequested("ch-1", "Introduction"), PushPayloads.parse(payload("chapter_status", "revisions"))?.event)
        assertNull(PushPayloads.parse(payload("chapter_status", "drafting")))
    }

    @Test
    fun `defense pushes carry the date`() {
        val notice = PushPayloads.parse(payload("defense_scheduled", "final", "", "at" to "1790000000000"))
        assertEquals(PushEvent.DefenseScheduled(DefenseKind.FINAL, Instant.ofEpochMilli(1_790_000_000_000)), notice?.event)
    }

    @Test
    fun `unknown, unaddressed or target-less pushes are dropped`() {
        assertNull(PushPayloads.parse(payload("chapter_renamed")))
        assertNull(PushPayloads.parse(payload("task_assigned") - "recipientId"))
        assertNull(PushPayloads.parse(payload("task_assigned", targetId = "")))
    }
}
