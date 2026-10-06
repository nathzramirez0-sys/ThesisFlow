package com.nathzramirez.thesisflow.notifications

import android.content.Context
import android.content.Intent
import com.nathzramirez.thesisflow.MainActivity

/** The screen a tapped notification opens. */
sealed interface NotificationLink {
    val groupId: String

    data class ToGroup(override val groupId: String) : NotificationLink
    data class ToChapter(override val groupId: String, val chapterId: String) : NotificationLink
    data class ToTask(override val groupId: String, val taskId: String) : NotificationLink

    companion object {
        private const val EXTRA_GROUP = "com.nathzramirez.thesisflow.notification.GROUP"
        private const val EXTRA_CHAPTER = "com.nathzramirez.thesisflow.notification.CHAPTER"
        private const val EXTRA_TASK = "com.nathzramirez.thesisflow.notification.TASK"

        /** Reads a link back from intent extras; [extra] is Intent.getStringExtra, swappable in tests. */
        fun fromExtras(extra: (String) -> String?): NotificationLink? {
            val groupId = extra(EXTRA_GROUP)?.takeIf { it.isNotBlank() } ?: return null
            extra(EXTRA_CHAPTER)?.takeIf { it.isNotBlank() }?.let { return ToChapter(groupId, it) }
            extra(EXTRA_TASK)?.takeIf { it.isNotBlank() }?.let { return ToTask(groupId, it) }
            return ToGroup(groupId)
        }

        fun fromIntent(intent: Intent?): NotificationLink? = intent?.let { fromExtras(it::getStringExtra) }

        /** Opens the app on the link, or brings the running app forward (MainActivity is singleTop). */
        fun intent(context: Context, link: NotificationLink): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_GROUP, link.groupId)
                when (link) {
                    is ToChapter -> putExtra(EXTRA_CHAPTER, link.chapterId)
                    is ToTask -> putExtra(EXTRA_TASK, link.taskId)
                    is ToGroup -> Unit
                }
            }
    }
}
