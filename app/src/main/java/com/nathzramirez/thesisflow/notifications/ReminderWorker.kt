package com.nathzramirez.thesisflow.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nathzramirez.thesisflow.domain.repository.ReminderLog
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import com.nathzramirez.thesisflow.domain.usecase.reminder.CollectDueRemindersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * The daily reminder. Reads only the phone's cache, so it needs no network and
 * runs offline; what it knows is what the app last synced.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val collectDueReminders: CollectDueRemindersUseCase,
    private val reminderLog: ReminderLog,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Without permission nothing is marked sent, so the reminders come once it's allowed.
        if (settings.notificationSettings.first().remindersEnabled && notifier.canPost()) {
            val due = collectDueReminders(Instant.now(), ZoneId.systemDefault())
            if (due.isNotEmpty()) {
                notifier.showReminders(due)
                reminderLog.markSent(due.map { it.key })
            }
        }
        reminderLog.prune(KEEP_DAYS)
        return Result.success()
    }

    private companion object {
        /** Long past every due date a key could belong to. */
        const val KEEP_DAYS = 60L
    }
}
