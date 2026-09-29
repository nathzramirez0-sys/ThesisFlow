package com.nathzramirez.thesisflow

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.nathzramirez.thesisflow.data.sync.FirestoreSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implements [Configuration.Provider] so WorkManager builds workers through Hilt,
 * which is how the upload worker gets its DAO and Firebase clients injected.
 * The default initializer is removed in AndroidManifest.xml for this to take effect.
 */
@HiltAndroidApp
class ThesisFlowApp : Application(), Configuration.Provider {

    @Inject
    lateinit var syncManager: FirestoreSyncManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Sync only while some screen of the app is visible; listeners cost reads and battery.
        val isInForeground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        syncManager.start(isInForeground)
    }
}
