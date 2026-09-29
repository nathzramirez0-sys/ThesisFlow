package com.nathzramirez.thesisflow.domain.repository

import com.nathzramirez.thesisflow.domain.model.Activity
import kotlinx.coroutines.flow.Flow

/** Read-only: only Cloud Functions write activity. */
interface ActivityRepository {
    /** Newest first. The phone keeps the latest [MAX_CACHED] entries per group. */
    fun observeRecent(groupId: String, limit: Int = MAX_CACHED): Flow<List<Activity>>

    companion object {
        const val MAX_CACHED = 100
    }
}
