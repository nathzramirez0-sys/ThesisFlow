package com.nathzramirez.thesisflow.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.NotificationSettings
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Backs the Notifications section of the profile and the "turn on notifications"
 * card. Changes only touch settings: the push registrar and the reminder
 * scheduler watch them and react on their own.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Null until the stored settings have loaded, so switches never flash the wrong way. */
    val state: StateFlow<NotificationSettings?> = settings.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPushEnabled(enabled: Boolean) = viewModelScope.launch { settings.setPushEnabled(enabled) }

    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch { settings.setRemindersEnabled(enabled) }

    fun setReminderTime(time: LocalTime) = viewModelScope.launch { settings.setReminderTime(time) }

    fun dismissPrompt() = viewModelScope.launch { settings.dismissPermissionPrompt() }
}
