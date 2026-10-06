package com.nathzramirez.thesisflow.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nathzramirez.thesisflow.data.di.ApplicationScope
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one periodic WorkManager job, daily at the chosen time, in step with the
 * settings. WorkManager survives reboots and app updates, and may run the job a
 * little late to save battery, which suits a morning summary.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // The time scheduled in this process; null at launch and while reminders are off.
            var scheduled: LocalTime? = null
            settings.notificationSettings
                .map { it.remindersEnabled to it.reminderTime }
                .distinctUntilChanged()
                .collect { (enabled, time) ->
                    if (!enabled) {
                        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                        scheduled = null
                        return@collect
                    }
                    // At launch, keep the existing job: replacing it could skip a run that is due
                    // but delayed. Only a new time replaces it.
                    val policy = if (scheduled == null) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                    enqueue(time, policy)
                    scheduled = time
                }
        }
    }

    private fun enqueue(time: LocalTime, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(time, ZonedDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        private const val WORK_NAME = "daily-reminders"

        /** Time until [at] next comes round on the clock: later today, or tomorrow once it has passed. */
        fun delayUntilNext(at: LocalTime, now: ZonedDateTime): Duration {
            val today = now.with(at.withSecond(0).withNano(0))
            val next = if (today.isAfter(now)) today else today.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
