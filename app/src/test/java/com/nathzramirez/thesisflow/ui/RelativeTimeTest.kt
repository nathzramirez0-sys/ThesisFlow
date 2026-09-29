package com.nathzramirez.thesisflow.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RelativeTimeTest {

    private val manila = ZoneId.of("Asia/Manila")
    private val now = ZonedDateTime.of(2026, 9, 30, 8, 0, 0, 0, manila).toInstant()

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, manila).toInstant()

    @Test
    fun `recent moments count minutes, then hours`() {
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now.minusSeconds(20), now, manila))
        assertEquals(RelativeTime.MinutesAgo(5), RelativeTime.of(at(30, 7, 55), now, manila))
        assertEquals(RelativeTime.HoursAgo(2), RelativeTime.of(at(30, 6, 0), now, manila))
    }

    @Test
    fun `late last night is yesterday, even under a day ago`() {
        assertEquals(RelativeTime.Yesterday, RelativeTime.of(at(29, 23, 0), now, manila))
    }

    @Test
    fun `older moments show their date`() {
        assertEquals(RelativeTime.On(LocalDate.of(2026, 9, 27)), RelativeTime.of(at(27, 15, 0), now, manila))
    }

    @Test
    fun `a clock behind the server's still reads just now`() {
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now.plusSeconds(30), now, manila))
    }
}
