package com.nathzramirez.thesisflow.domain.model

import java.time.LocalTime

/** Per-device notification choices; they stay with the phone, not the account. */
data class NotificationSettings(
    /** Pushes about the group: feedback, reviews, tasks, joins. */
    val pushEnabled: Boolean = true,
    /** The daily summary of what's due, worked out on the phone. */
    val remindersEnabled: Boolean = true,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    /** The "turn on notifications" card was dismissed, so it isn't shown again. */
    val permissionPromptDismissed: Boolean = false,
) {
    companion object {
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(7, 0)
    }
}
