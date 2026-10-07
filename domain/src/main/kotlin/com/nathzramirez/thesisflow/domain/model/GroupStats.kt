package com.nathzramirez.thesisflow.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * What one student has done that the app can see. These are counts of recorded
 * actions, not a measure of effort, and the dashboard says so.
 */
data class MemberContribution(
    val member: Member,
    /** Finished tasks they were assigned; a task shared by two counts for both. */
    val tasksDone: Int,
    val draftsUploaded: Int,
    val feedbackResolved: Int,
    val comments: Int,
    val openTasks: Int,
    val overdueTasks: Int,
) {
    val total: Int get() = tasksDone + draftsUploaded + feedbackResolved
}

/** Work finished in one week, Monday to Sunday in the phone's time zone. */
data class WeekActivity(
    val weekStart: LocalDate,
    val tasksDone: Int,
    val draftsUploaded: Int,
    val feedbackResolved: Int,
) {
    val total: Int get() = tasksDone + draftsUploaded + feedbackResolved
}

data class GroupStats(
    val progress: ThesisProgress,
    val chaptersByStatus: Map<ChapterStatus, Int>,
    val tasksByStatus: Map<TaskStatus, Int>,
    val overdueTasks: Int,
    val openFeedback: Int,
    val resolvedFeedback: Int,
    /** The median time from feedback being posted to being resolved; null until something is resolved. */
    val typicalResolution: Duration?,
    /** Current students only, by name: a dashboard, deliberately not a leaderboard. */
    val contributions: List<MemberContribution>,
    /** The last [GroupStatsCalculator.WEEKS] weeks, oldest first, ending with this week. */
    val weeks: List<WeekActivity>,
) {
    val tasksTotal: Int get() = tasksByStatus.values.sum()
    val tasksDone: Int get() = tasksByStatus[TaskStatus.DONE] ?: 0
}

/**
 * Builds the dashboard from rows already on the phone, so it works offline and
 * needs no extra reads. Pure, so every rule below is unit-tested.
 */
object GroupStatsCalculator {
    const val WEEKS = 8

    @Suppress("LongParameterList")
    fun compute(
        now: Instant,
        zone: ZoneId,
        members: List<Member>,
        chapters: List<Chapter>,
        versions: List<ChapterVersion>,
        tasks: List<Task>,
        feedback: List<Feedback>,
        comments: List<TaskComment>,
    ): GroupStats {
        val doneTasks = tasks.filter { it.status == TaskStatus.DONE }
        val openTasks = tasks.filter { it.status != TaskStatus.DONE }
        val resolved = feedback.filter { it.resolved }

        val contributions = members
            .filter { it.role != Role.ADVISER }
            .sortedBy { it.displayName.lowercase() }
            .map { member ->
                val uid = member.uid
                MemberContribution(
                    member = member,
                    tasksDone = doneTasks.count { uid in it.assigneeIds },
                    draftsUploaded = versions.count { it.uploadedBy == uid },
                    feedbackResolved = resolved.count { it.resolvedBy == uid },
                    comments = comments.count { it.authorId == uid },
                    openTasks = openTasks.count { uid in it.assigneeIds },
                    overdueTasks = openTasks.count { uid in it.assigneeIds && it.isOverdue(now) },
                )
            }

        return GroupStats(
            progress = ThesisProgress.of(chapters),
            chaptersByStatus = ChapterStatus.entries.associateWith { status -> chapters.count { it.status == status } },
            tasksByStatus = TaskStatus.entries.associateWith { status -> tasks.count { it.status == status } },
            overdueTasks = openTasks.count { it.isOverdue(now) },
            openFeedback = feedback.size - resolved.size,
            resolvedFeedback = resolved.size,
            typicalResolution = median(
                resolved.mapNotNull { item ->
                    val posted = item.createdAt ?: return@mapNotNull null
                    val done = item.resolvedAt ?: return@mapNotNull null
                    Duration.between(posted, done).takeUnless { it.isNegative }
                },
            ),
            contributions = contributions,
            weeks = weeks(now, zone, doneTasks, versions, resolved),
        )
    }

    private fun weeks(
        now: Instant,
        zone: ZoneId,
        doneTasks: List<Task>,
        versions: List<ChapterVersion>,
        resolved: List<Feedback>,
    ): List<WeekActivity> {
        fun weekOf(instant: Instant): LocalDate =
            instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val thisWeek = weekOf(now)
        val starts = (WEEKS - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
        val tasksByWeek = doneTasks.mapNotNull { it.completedAt }.groupingBy(::weekOf).eachCount()
        val draftsByWeek = versions.mapNotNull { it.uploadedAt }.groupingBy(::weekOf).eachCount()
        val resolvedByWeek = resolved.mapNotNull { it.resolvedAt }.groupingBy(::weekOf).eachCount()
        return starts.map { start ->
            WeekActivity(
                weekStart = start,
                tasksDone = tasksByWeek[start] ?: 0,
                draftsUploaded = draftsByWeek[start] ?: 0,
                feedbackResolved = resolvedByWeek[start] ?: 0,
            )
        }
    }

    /** The middle value, so one comment left open for a month doesn't skew the typical time. */
    private fun median(durations: List<Duration>): Duration? {
        if (durations.isEmpty()) return null
        val sorted = durations.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else sorted[middle - 1].plus(sorted[middle]).dividedBy(2)
    }
}
