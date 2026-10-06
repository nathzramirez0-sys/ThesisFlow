package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface SettingsRepository {
    val notificationSettings: Flow<NotificationSettings>

    suspend fun setPushEnabled(enabled: Boolean)

    suspend fun setRemindersEnabled(enabled: Boolean)

    suspend fun setReminderTime(time: LocalTime)

    suspend fun dismissPermissionPrompt()
}
