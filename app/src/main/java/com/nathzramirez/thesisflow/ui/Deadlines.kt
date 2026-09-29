package com.nathzramirez.thesisflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nathzramirez.thesisflow.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** How a deadline reads, relative to today. Pure, so it's unit-tested without Android. */
sealed interface DeadlineLabel {
    data object DueToday : DeadlineLabel
    data object DueTomorrow : DeadlineLabel
    data class DueInDays(val days: Int) : DeadlineLabel
    data class DueOn(val date: LocalDate) : DeadlineLabel
    data class Overdue(val days: Int) : DeadlineLabel

    companion object {
        /** A week out or more reads better as a date than as a count of days. */
        private const val COUNTDOWN_DAYS = 7

        /**
         * Compares calendar days in the phone's time zone, so "tomorrow" means
         * tomorrow's date, not "within 24 hours".
         */
        fun of(deadline: Instant, now: Instant, zone: ZoneId, done: Boolean): DeadlineLabel {
            val today = now.atZone(zone).toLocalDate()
            val due = deadline.atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(today, due).toInt()
            return when {
                done -> DueOn(due)
                now.isAfter(deadline) -> Overdue(maxOf(0, -days))
                days == 0 -> DueToday
                days == 1 -> DueTomorrow
                days < COUNTDOWN_DAYS -> DueInDays(days)
                else -> DueOn(due)
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun DeadlineLabel.text(): String = when (this) {
    DeadlineLabel.DueToday -> stringResource(R.string.deadline_today)
    DeadlineLabel.DueTomorrow -> stringResource(R.string.deadline_tomorrow)
    is DeadlineLabel.DueInDays -> pluralStringResource(R.plurals.deadline_in_days, days, days)
    is DeadlineLabel.DueOn -> stringResource(R.string.deadline_on, dateFormatter.format(date))
    is DeadlineLabel.Overdue ->
        if (days == 0) stringResource(R.string.deadline_overdue_today)
        else pluralStringResource(R.plurals.deadline_overdue, days, days)
}

/** The end of a picked calendar day, so a deadline "on the 5th" lasts all day. */
fun endOfDay(date: LocalDate, zone: ZoneId): Instant = date.plusDays(1).atStartOfDay(zone).minusSeconds(1).toInstant()
