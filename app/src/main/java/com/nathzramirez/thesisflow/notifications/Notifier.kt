package com.nathzramirez.thesisflow.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.PushEvent
import com.nathzramirez.thesisflow.domain.model.PushNotice
import com.nathzramirez.thesisflow.domain.model.Reminder
import com.nathzramirez.thesisflow.domain.model.ReminderSubject
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.Urgency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Turns pushes and reminders into system notifications. The only place the app posts any. */
@Singleton
class Notifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Starts from the clock, so ids from before a restart aren't reused and overwritten. */
    private val nextId = AtomicInteger((System.currentTimeMillis() / 1000 % 1_000_000).toInt())

    /** False until the user allows notifications (Android 13+) or when they've turned them off. */
    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )

    fun show(notice: PushNotice) {
        val actor = notice.actorName.ifBlank { context.getString(R.string.activity_someone) }
        val content = when (val event = notice.event) {
            is PushEvent.MemberJoined -> Content(
                NotificationChannels.GROUP,
                text(R.string.push_member_joined_title),
                text(R.string.push_member_joined, actor, text(roleLabel(event.role))),
                NotificationLink.ToGroup(notice.groupId),
            )
            is PushEvent.ReadyForReview -> Content(
                NotificationChannels.REVIEWS,
                text(R.string.push_review_title),
                text(R.string.push_review, actor, event.chapterTitle),
                NotificationLink.ToChapter(notice.groupId, event.chapterId),
            )
            is PushEvent.ChapterApproved -> Content(
                NotificationChannels.REVIEWS,
                text(R.string.push_approved_title),
                text(R.string.push_approved, actor, event.chapterTitle),
                NotificationLink.ToChapter(notice.groupId, event.chapterId),
            )
            is PushEvent.RevisionsRequested -> Content(
                NotificationChannels.REVIEWS,
                text(R.string.push_revisions_title),
                text(R.string.push_revisions, actor, event.chapterTitle),
                NotificationLink.ToChapter(notice.groupId, event.chapterId),
            )
            is PushEvent.FeedbackPosted -> Content(
                NotificationChannels.REVIEWS,
                event.versionNumber?.let { text(R.string.push_feedback_version_title, it, event.chapterTitle) }
                    ?: text(R.string.push_feedback_title, event.chapterTitle),
                text(R.string.push_feedback, actor, event.preview),
                NotificationLink.ToChapter(notice.groupId, event.chapterId),
            )
            is PushEvent.FeedbackReopened -> Content(
                NotificationChannels.REVIEWS,
                text(R.string.push_reopened_title),
                text(R.string.push_reopened, actor, event.chapterTitle),
                NotificationLink.ToChapter(notice.groupId, event.chapterId),
            )
            is PushEvent.FeedbackResolved -> Content(
                NotificationChannels.REVIEWS,
                text(R.string.push_resolved_title),
                text(R.string.push_resolved, actor, event.chapterTitle),
                NotificationLink.ToChapter(notice.groupId, event.chapterId),
            )
            is PushEvent.TaskAssigned -> Content(
                NotificationChannels.TASKS,
                text(R.string.push_assigned_title),
                text(R.string.push_assigned, actor, event.taskTitle),
                NotificationLink.ToTask(notice.groupId, event.taskId),
            )
            is PushEvent.TaskDone -> Content(
                NotificationChannels.TASKS,
                text(R.string.push_task_done_title),
                text(R.string.push_task_done, actor, event.taskTitle),
                NotificationLink.ToTask(notice.groupId, event.taskId),
            )
            is PushEvent.DefenseScheduled -> Content(
                NotificationChannels.GROUP,
                text(
                    when (event.kind) {
                        DefenseKind.PROPOSAL -> R.string.push_defense_proposal_title
                        DefenseKind.FINAL -> R.string.push_defense_final_title
                    },
                ),
                text(R.string.push_defense, actor, dateTimeFormatter.format(event.at.atZone(ZoneId.systemDefault()))),
                NotificationLink.ToGroup(notice.groupId),
            )
        }
        post(nextId.incrementAndGet(), content, subText = notice.groupName, groupKey = notice.groupId)
    }

    /**
     * One notification per group: a single reminder says it all in the title,
     * several become a list. Each group's digest replaces yesterday's.
     */
    fun showReminders(reminders: List<Reminder>) {
        reminders.groupBy { it.groupId }.forEach { (groupId, items) ->
            val lines = items.map(::reminderLine)
            val first = items.first()
            val link = if (items.size == 1) linkOf(first) else NotificationLink.ToGroup(groupId)
            val title = if (items.size == 1) {
                lines.first()
            } else {
                context.resources.getQuantityString(R.plurals.reminders_title, items.size, items.size, first.groupName)
            }
            post(
                id = "reminders:$groupId".hashCode(),
                content = Content(NotificationChannels.REMINDERS, title, if (items.size == 1) first.groupName else lines.first(), link),
                subText = first.groupName,
                groupKey = groupId,
                lines = lines.takeIf { it.size > 1 },
            )
        }
    }

    private fun reminderLine(reminder: Reminder): String = when (val subject = reminder.subject) {
        is ReminderSubject.TaskDue -> when (reminder.urgency) {
            Urgency.OVERDUE -> text(R.string.reminder_task_overdue, subject.title)
            Urgency.TODAY -> text(R.string.reminder_task_today, subject.title)
            Urgency.TOMORROW, Urgency.SOON -> text(R.string.reminder_task_tomorrow, subject.title)
        }
        is ReminderSubject.ChapterDeadline -> when (reminder.urgency) {
            Urgency.OVERDUE -> text(R.string.reminder_chapter_overdue, subject.title)
            Urgency.TODAY -> text(R.string.reminder_chapter_today, subject.title)
            Urgency.TOMORROW -> text(R.string.reminder_chapter_tomorrow, subject.title)
            Urgency.SOON -> context.resources.getQuantityString(
                R.plurals.reminder_chapter_soon, reminder.daysLeft, reminder.daysLeft, subject.title,
            )
        }
        is ReminderSubject.DefenseDay -> {
            val kind = text(
                when (subject.kind) {
                    DefenseKind.PROPOSAL -> R.string.defense_kind_proposal
                    DefenseKind.FINAL -> R.string.defense_kind_final
                },
            )
            when (reminder.urgency) {
                Urgency.TODAY, Urgency.OVERDUE -> text(R.string.reminder_defense_today, kind)
                Urgency.TOMORROW -> text(R.string.reminder_defense_tomorrow, kind)
                Urgency.SOON -> context.resources.getQuantityString(
                    R.plurals.reminder_defense_soon, reminder.daysLeft, reminder.daysLeft, kind,
                )
            }
        }
    }

    private fun linkOf(reminder: Reminder): NotificationLink = when (val subject = reminder.subject) {
        is ReminderSubject.TaskDue -> NotificationLink.ToTask(reminder.groupId, subject.taskId)
        is ReminderSubject.ChapterDeadline -> NotificationLink.ToChapter(reminder.groupId, subject.chapterId)
        is ReminderSubject.DefenseDay -> NotificationLink.ToGroup(reminder.groupId)
    }

    // canPost() checks the permission first; lint can't see that through the early return.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, content: Content, subText: String, groupKey: String, lines: List<String>? = null) {
        if (!canPost()) return
        val tap = PendingIntent.getActivity(
            context,
            id,
            NotificationLink.intent(context, content.link),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = if (lines != null) {
            NotificationCompat.InboxStyle().also { inbox -> lines.forEach(inbox::addLine) }
        } else {
            NotificationCompat.BigTextStyle().bigText(content.text)
        }
        val notification = NotificationCompat.Builder(context, content.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_violet))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(subText.ifBlank { null })
            .setStyle(style)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setCategory(
                if (content.channel == NotificationChannels.REMINDERS) NotificationCompat.CATEGORY_REMINDER
                else NotificationCompat.CATEGORY_SOCIAL,
            )
            .build()
        manager.notify(id, notification)
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun roleLabel(role: Role): Int = when (role) {
        Role.LEADER -> R.string.role_leader
        Role.MEMBER -> R.string.role_member
        Role.ADVISER -> R.string.role_adviser
    }

    private data class Content(val channel: String, val title: String, val text: String, val link: NotificationLink)

    private companion object {
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
}
