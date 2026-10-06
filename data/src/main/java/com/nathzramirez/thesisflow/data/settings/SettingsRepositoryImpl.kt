package com.nathzramirez.thesisflow.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.nathzramirez.thesisflow.domain.model.NotificationSettings
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device settings in Preferences DataStore rather than SharedPreferences: reads
 * and writes are asynchronous (never on the main thread) and changes arrive as
 * a Flow. They are kept on sign-out, because they belong to the phone.
 */
@Singleton
internal class SettingsRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val notificationSettings: Flow<NotificationSettings> = store.data
        // A corrupt or unreadable file falls back to the defaults instead of crashing.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            NotificationSettings(
                pushEnabled = prefs[PUSH_ENABLED] ?: true,
                remindersEnabled = prefs[REMINDERS_ENABLED] ?: true,
                reminderTime = prefs[REMINDER_MINUTES]?.let { LocalTime.ofSecondOfDay(it * 60L) }
                    ?: NotificationSettings.DEFAULT_REMINDER_TIME,
                permissionPromptDismissed = prefs[PROMPT_DISMISSED] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setPushEnabled(enabled: Boolean) {
        store.edit { it[PUSH_ENABLED] = enabled }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        store.edit { it[REMINDERS_ENABLED] = enabled }
    }

    override suspend fun setReminderTime(time: LocalTime) {
        store.edit { it[REMINDER_MINUTES] = time.hour * 60 + time.minute }
    }

    override suspend fun dismissPermissionPrompt() {
        store.edit { it[PROMPT_DISMISSED] = true }
    }

    private companion object {
        val PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val PROMPT_DISMISSED = booleanPreferencesKey("notification_prompt_dismissed")
    }
}
