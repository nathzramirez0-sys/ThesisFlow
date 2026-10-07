package com.nathzramirez.thesisflow.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether the phone has a network right now. Only for telling the user; writes queue either way. */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
