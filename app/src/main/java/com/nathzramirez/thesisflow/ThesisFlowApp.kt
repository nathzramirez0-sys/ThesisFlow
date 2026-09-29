package com.nathzramirez.thesisflow

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.nathzramirez.thesisflow.data.sync.FirestoreSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltAndroidApp
class ThesisFlowApp : Application() {

    @Inject
    lateinit var syncManager: FirestoreSyncManager

    override fun onCreate() {
        super.onCreate()
        // Sync only while some screen of the app is visible; listeners cost reads and battery.
        val isInForeground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        syncManager.start(isInForeground)
    }
}
