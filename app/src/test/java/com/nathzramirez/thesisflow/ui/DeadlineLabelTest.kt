package com.nathzramirez.thesisflow.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DeadlineLabelTest {

    private val manila = ZoneId.of("Asia/Manila")
    private val now = ZonedDateTime.of(2026, 9, 29, 21, 0, 0, 0, manila).toInstant()

    private fun dueOn(year: Int, month: Int, day: Int) = endOfDay(LocalDate.of(year, month, day), manila)

    private fun label(deadlineDay: Int, month: Int = 9, done: Boolean = false) =
        DeadlineLabel.of(dueOn(2026, month, deadlineDay), now, manila, done)

    @Test
    fun `same day and next day read as words`() {
        assertEquals(DeadlineLabel.DueToday, label(29))
        assertEquals(DeadlineLabel.DueTomorrow, label(30))
    }

    @Test
    fun `the coming week counts down, later dates show the date`() {
        assertEquals(DeadlineLabel.DueInDays(3), label(2, month = 10))
        assertEquals(DeadlineLabel.DueOn(LocalDate.of(2026, 10, 20)), label(20, month = 10))
    }

    @Test
    fun `past deadlines count overdue days`() {
        assertEquals(DeadlineLabel.Overdue(2), label(27))
    }

    @Test
    fun `approved chapters just show their date, never overdue`() {
        assertEquals(DeadlineLabel.DueOn(LocalDate.of(2026, 9, 27)), label(27, done = true))
    }

    @Test
    fun `a deadline lasts until the end of its day`() {
        val end = dueOn(2026, 9, 29)
        assertEquals(ZonedDateTime.of(2026, 9, 29, 23, 59, 59, 0, manila).toInstant(), end)
    }
}
