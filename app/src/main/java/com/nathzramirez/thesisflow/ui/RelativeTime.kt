package com.nathzramirez.thesisflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nathzramirez.thesisflow.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** How long ago something happened, for feeds. Pure, so it's unit-tested without Android. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class MinutesAgo(val minutes: Int) : RelativeTime
    data class HoursAgo(val hours: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class On(val date: LocalDate) : RelativeTime

    companion object {
        /**
         * Minutes and hours while it's recent, then calendar days in the phone's
         * time zone, so something from 11 pm reads "Yesterday" at 8 am.
         * A clock slightly behind the server's gives a negative age: "Just now".
         */
        fun of(then: Instant, now: Instant, zone: ZoneId): RelativeTime {
            val age = Duration.between(then, now)
            val days = ChronoUnit.DAYS.between(then.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
            return when {
                age.toMinutes() < 1 -> JustNow
                age.toHours() < 1 -> MinutesAgo(age.toMinutes().toInt())
                days == 0L -> HoursAgo(age.toHours().toInt())
                days == 1L -> Yesterday
                else -> On(then.atZone(zone).toLocalDate())
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun RelativeTime.text(): String = when (this) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.MinutesAgo -> pluralStringResource(R.plurals.time_minutes_ago, minutes, minutes)
    is RelativeTime.HoursAgo -> pluralStringResource(R.plurals.time_hours_ago, hours, hours)
    RelativeTime.Yesterday -> stringResource(R.string.time_yesterday)
    is RelativeTime.On -> dateFormatter.format(date)
}

/** Empty for a missing timestamp, which only happens for a moment while a write syncs. */
@Composable
fun relativeTimeText(then: Instant?, now: Instant): String =
    then?.let { RelativeTime.of(it, now, ZoneId.systemDefault()).text() }.orEmpty()
