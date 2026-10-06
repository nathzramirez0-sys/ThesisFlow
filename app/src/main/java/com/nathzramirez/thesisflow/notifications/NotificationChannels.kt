package com.nathzramirez.thesisflow.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.nathzramirez.thesisflow.R

/**
 * One channel per kind of news, so people can silence task pings but keep
 * adviser feedback loud, from the system settings. Creating them is idempotent,
 * so it runs on every app start and picks up renamed labels.
 */
object NotificationChannels {
    const val REVIEWS = "reviews"
    const val TASKS = "tasks"
    const val REMINDERS = "reminders"
    const val GROUP = "group"

    fun create(context: Context) {
        fun channel(id: String, importance: Int, name: Int, description: Int) =
            NotificationChannelCompat.Builder(id, importance)
                .setName(context.getString(name))
                .setDescription(context.getString(description))
                .build()

        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                channel(REVIEWS, NotificationManagerCompat.IMPORTANCE_HIGH, R.string.channel_reviews, R.string.channel_reviews_body),
                channel(TASKS, NotificationManagerCompat.IMPORTANCE_DEFAULT, R.string.channel_tasks, R.string.channel_tasks_body),
                channel(REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT, R.string.channel_reminders, R.string.channel_reminders_body),
                channel(GROUP, NotificationManagerCompat.IMPORTANCE_DEFAULT, R.string.channel_group, R.string.channel_group_body),
            ),
        )
    }
}
