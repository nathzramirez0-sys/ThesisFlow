package com.nathzramirez.thesisflow.feature.activity

import com.nathzramirez.thesisflow.domain.model.Activity
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.Chapter
import com.nathzramirez.thesisflow.domain.model.Task

/** Where tapping an entry goes. */
sealed interface ActivityLink {
    /** [number] is the chapter's position, shown as "Chapter 03" on the detail screen. */
    data class ToChapter(val chapterId: String, val number: Int) : ActivityLink
    data class ToTask(val taskId: String) : ActivityLink
}

/** An entry as the feed shows it; [link] is null when there is nothing to open. */
data class ActivityEntry(val activity: Activity, val link: ActivityLink?)

/**
 * Pairs entries with what they point at. A chapter or task deleted since then
 * gets no link, so the row stays readable but doesn't open an empty screen.
 * [chapters] must be in list order, which is where chapter numbers come from.
 */
fun linkActivities(activities: List<Activity>, chapters: List<Chapter>, tasks: List<Task>): List<ActivityEntry> {
    val chapterNumbers = chapters.withIndex().associate { (index, chapter) -> chapter.id to index + 1 }
    val taskIds = tasks.mapTo(HashSet()) { it.id }
    return activities.map { activity ->
        val link = when (val event = activity.event) {
            is ActivityEvent.ChapterEvent ->
                chapterNumbers[event.chapterId]?.let { ActivityLink.ToChapter(event.chapterId, it) }
            is ActivityEvent.TaskEvent -> event.taskId.takeIf { it in taskIds }?.let(ActivityLink::ToTask)
            is ActivityEvent.MemberJoined, ActivityEvent.MemberLeft -> null
        }
        ActivityEntry(activity, link)
    }
}
