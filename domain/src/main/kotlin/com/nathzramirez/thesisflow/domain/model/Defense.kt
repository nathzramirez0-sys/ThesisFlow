package com.nathzramirez.thesisflow.domain.model

import java.time.Duration
import java.time.Instant

/** The two panel defenses of a Philippine undergraduate thesis. */
enum class DefenseKind { PROPOSAL, FINAL }

data class Defense(val kind: DefenseKind, val at: Instant)

/** The group's defense dates, in order. */
object DefenseSchedule {

    fun of(group: Group): List<Defense> = listOfNotNull(
        group.proposalDefenseAt?.let { Defense(DefenseKind.PROPOSAL, it) },
        group.finalDefenseAt?.let { Defense(DefenseKind.FINAL, it) },
    ).sortedBy { it.at }

    /** The next defense that hasn't started yet, or null when none is set or all are past. */
    fun next(group: Group, now: Instant): Defense? = of(group).firstOrNull { it.at.isAfter(now) }
}

/** Time left, split for a days/hours/minutes display. */
data class Countdown(val days: Long, val hours: Int, val minutes: Int) {
    companion object {
        /**
         * Rounded up to the minute, so the display reaches zero only when the
         * defense starts, never a minute early. Zero once it has passed.
         */
        fun until(at: Instant, now: Instant): Countdown {
            val seconds = Duration.between(now, at).seconds.coerceAtLeast(0)
            val minutes = (seconds + 59) / 60
            return Countdown(days = minutes / (24 * 60), hours = ((minutes / 60) % 24).toInt(), minutes = (minutes % 60).toInt())
        }
    }
}
