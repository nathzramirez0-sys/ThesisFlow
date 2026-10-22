package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.sync.BackgroundServices
import com.nathzramirez.thesisflow.domain.repository.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Nothing to sync and no push token to keep: the data never leaves the phone. */
internal class LocalBackgroundServices @Inject constructor() : BackgroundServices {
    override fun start(isInForeground: Flow<Boolean>) = Unit
}

/** Nothing in the local build needs a connection, so it never shows the offline strip. */
internal class AlwaysOnlineNetworkMonitor @Inject constructor() : NetworkMonitor {
    override val isOnline: Flow<Boolean> = flowOf(true)
}
