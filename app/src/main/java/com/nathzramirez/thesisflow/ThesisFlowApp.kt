package com.nathzramirez.thesisflow

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.nathzramirez.thesisflow.data.sync.BackgroundServices
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

    /** Firestore sync and push registration in the cloud build; nothing in the local one. */
    @Inject
    lateinit var backgroundServices: BackgroundServices

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminders: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val isInForeground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        backgroundServices.start(isInForeground)
        NotificationChannels.create(this)
        reminders.start()
    }
}
