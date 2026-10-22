package com.nathzramirez.thesisflow.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * What the data layer keeps running while the app is alive. The cloud build
 * syncs Firestore while a screen is visible and keeps the push token registered;
 * the local build has nothing to sync, so its version does nothing.
 */
interface BackgroundServices {
    fun start(isInForeground: Flow<Boolean>)
}
