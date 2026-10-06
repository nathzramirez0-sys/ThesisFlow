package com.nathzramirez.thesisflow.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How close something is, in calendar days. Each step reminds once. */
enum class Urgency { OVERDUE, TODAY, TOMORROW, SOON }

sealed interface ReminderSubject {
    data class TaskDue(val taskId: String, val title: String) : ReminderSubject
    data class ChapterDeadline(val chapterId: String, val title: String) : ReminderSubject
    data class DefenseDay(val kind: DefenseKind) : ReminderSubject
}

data class Reminder(
    /** Unique per item, urgency step and due date, so each step fires once and a moved date starts over. */
    val key: String,
    val groupId: String,
    val groupName: String,
    val subject: ReminderSubject,
    val urgency: Urgency,
    /** Calendar days from today to the due date; negative when overdue. */
    val daysLeft: Int,
    val due: Instant,
)

/**
 * Decides what the daily reminder should mention. Pure, so the rules are unit-tested
 * and the worker only has to load data and post notifications.
 *
 * - Tasks assigned to you: the day before, the day itself, and once when overdue.
 * - Chapter deadlines, for students: three days ahead, the day before, the day itself,
 *   and once when overdue. Approved chapters are left alone.
 * - Defenses, for everyone in the group: a week ahead, the day before, and the day itself.
 */
object ReminderPlanner {
    const val CHAPTER_SOON_DAYS = 3
    const val DEFENSE_SOON_DAYS = 7

    fun plan(
        now: Instant,
        zone: ZoneId,
        uid: String,
        groups: List<Group>,
        chapters: List<Chapter>,
        tasks: List<Task>,
    ): List<Reminder> {
        val today = now.atZone(zone).toLocalDate()
        val groupsById = groups.associateBy { it.id }
        val reminders = mutableListOf<Reminder>()

        fun add(group: Group, id: String, subject: ReminderSubject, due: Instant, soonDays: Int, remindOverdue: Boolean) {
            val dueDate = due.atZone(zone).toLocalDate()
            val daysLeft = ChronoUnit.DAYS.between(today, dueDate).toInt()
            val urgency = urgencyOf(daysLeft, soonDays, remindOverdue) ?: return
            reminders += Reminder(
                key = keyOf(id, urgency, dueDate),
                groupId = group.id,
                groupName = group.name,
                subject = subject,
                urgency = urgency,
                daysLeft = daysLeft,
                due = due,
            )
        }

        for (task in tasks) {
            val group = groupsById[task.groupId] ?: continue
            val due = task.dueAt ?: continue
            if (task.status == TaskStatus.DONE || uid !in task.assigneeIds) continue
            add(group, "task:${task.id}", ReminderSubject.TaskDue(task.id, task.title), due, soonDays = 1, remindOverdue = true)
        }
        for (chapter in chapters) {
            val group = groupsById[chapter.groupId] ?: continue
            val due = chapter.deadline ?: continue
            if (group.myRole == Role.ADVISER || chapter.status == ChapterStatus.APPROVED) continue
            add(
                group, "chapter:${chapter.id}", ReminderSubject.ChapterDeadline(chapter.id, chapter.title), due,
                soonDays = CHAPTER_SOON_DAYS, remindOverdue = true,
            )
        }
        for (group in groups) {
            for (defense in DefenseSchedule.of(group)) {
                add(
                    group, "defense:${group.id}:${defense.kind.name}", ReminderSubject.DefenseDay(defense.kind), defense.at,
                    soonDays = DEFENSE_SOON_DAYS, remindOverdue = false,
                )
            }
        }
        return reminders.sortedWith(compareBy({ it.groupName }, { it.urgency.ordinal }, { it.due }))
    }

    private fun urgencyOf(daysLeft: Int, soonDays: Int, remindOverdue: Boolean): Urgency? = when {
        daysLeft < 0 -> if (remindOverdue) Urgency.OVERDUE else null
        daysLeft == 0 -> Urgency.TODAY
        daysLeft == 1 -> Urgency.TOMORROW
        daysLeft <= soonDays -> Urgency.SOON
        else -> null
    }

    private fun keyOf(id: String, urgency: Urgency, dueDate: LocalDate) = "$id:${urgency.name}:$dueDate"
}
