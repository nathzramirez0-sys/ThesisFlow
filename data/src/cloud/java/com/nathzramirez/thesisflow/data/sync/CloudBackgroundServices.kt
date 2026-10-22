package com.nathzramirez.thesisflow.data.sync

import com.nathzramirez.thesisflow.data.push.PushTokenRegistrar
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Firestore listeners while a screen is visible, and this phone's push token kept on the server. */
internal class CloudBackgroundServices @Inject constructor(
    private val syncManager: FirestoreSyncManager,
    private val pushTokens: PushTokenRegistrar,
) : BackgroundServices {

    override fun start(isInForeground: Flow<Boolean>) {
        // Sync only while some screen of the app is visible; listeners cost reads and battery.
        syncManager.start(isInForeground)
        pushTokens.start()
    }
}
