package com.nathzramirez.thesisflow

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.nathzramirez.thesisflow.data.push.PushTokenRegistrar
import com.nathzramirez.thesisflow.data.sync.FirestoreSyncManager
import com.nathzramirez.thesisflow.notifications.NotificationChannels
import com.nathzramirez.thesisflow.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implements [Configuration.Provider] so WorkManager builds workers through Hilt,
 * which is how the upload and reminder workers get their dependencies injected.
 * The default initializer is removed in AndroidManifest.xml for this to take effect.
 */
@HiltAndroidApp
class ThesisFlowApp : Application(), Configuration.Provider {

    @Inject
    lateinit var syncManager: FirestoreSyncManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var pushTokens: PushTokenRegistrar

    @Inject
    lateinit var reminders: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Sync only while some screen of the app is visible; listeners cost reads and battery.
        val isInForeground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        syncManager.start(isInForeground)
        NotificationChannels.create(this)
        pushTokens.start()
        reminders.start()
    }
}
