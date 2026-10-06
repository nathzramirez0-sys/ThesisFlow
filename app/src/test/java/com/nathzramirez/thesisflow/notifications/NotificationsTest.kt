package com.nathzramirez.thesisflow.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {

    private val manila = ZoneId.of("Asia/Manila")

    @Test
    fun `the first run is later today when the time hasn't come yet`() {
        val now = ZonedDateTime.of(2026, 10, 6, 6, 30, 0, 0, manila)
        assertEquals(Duration.ofMinutes(30), ReminderScheduler.delayUntilNext(LocalTime.of(7, 0), now))
    }

    @Test
    fun `once the time has passed, the first run is tomorrow`() {
        val now = ZonedDateTime.of(2026, 10, 6, 7, 0, 0, 0, manila)
        assertEquals(Duration.ofHours(24), ReminderScheduler.delayUntilNext(LocalTime.of(7, 0), now))
        val evening = ZonedDateTime.of(2026, 10, 6, 21, 15, 0, 0, manila)
        assertEquals(Duration.ofHours(9).plusMinutes(45), ReminderScheduler.delayUntilNext(LocalTime.of(7, 0), evening))
    }
}

class NotificationLinkTest {

    private fun extras(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { key -> map.entries.firstOrNull { key.endsWith(it.key) }?.value }
    }

    @Test
    fun `extras name the most specific screen`() {
        assertEquals(NotificationLink.ToChapter("g-1", "ch-1"), NotificationLink.fromExtras(extras("GROUP" to "g-1", "CHAPTER" to "ch-1")))
        assertEquals(NotificationLink.ToTask("g-1", "t-1"), NotificationLink.fromExtras(extras("GROUP" to "g-1", "TASK" to "t-1")))
        assertEquals(NotificationLink.ToGroup("g-1"), NotificationLink.fromExtras(extras("GROUP" to "g-1")))
    }

    @Test
    fun `an ordinary launch is not a notification`() {
        assertNull(NotificationLink.fromExtras(extras()))
        assertNull(NotificationLink.fromExtras(extras("CHAPTER" to "ch-1")))
    }
}
