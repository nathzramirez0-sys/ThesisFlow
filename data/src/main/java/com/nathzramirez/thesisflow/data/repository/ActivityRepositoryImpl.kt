package com.nathzramirez.thesisflow.data.repository

import com.nathzramirez.thesisflow.data.local.dao.ActivityDao
import com.nathzramirez.thesisflow.data.local.toDomain
import com.nathzramirez.thesisflow.domain.model.Activity
import com.nathzramirez.thesisflow.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the synced feed; there is nothing to write, because only the server writes activity. */
@Singleton
internal class ActivityRepositoryImpl @Inject constructor(
    private val activityDao: ActivityDao,
) : ActivityRepository {

    override fun observeRecent(groupId: String, limit: Int): Flow<List<Activity>> =
        activityDao.observeRecent(groupId, limit).map { rows -> rows.mapNotNull { it.toDomain() } }
}
