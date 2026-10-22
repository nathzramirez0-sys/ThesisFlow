package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.local.dao.ActivityDao
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.ActivityEntity
import java.time.Instant
import javax.inject.Inject

/**
 * Writes the group activity feed. In the cloud build, Firestore triggers do this
 * (functions/src/activity.ts). Here each repository records what it just did,
 * with the same types, actors and details, so the feed reads both builds alike.
 */
internal class LocalActivityLog @Inject constructor(
    private val activityDao: ActivityDao,
    private val localModeDao: LocalModeDao,
    private val userDao: UserDao,
) {

    suspend fun record(
        groupId: String,
        type: String,
        actorId: String,
        targetId: String? = null,
        targetTitle: String = "",
        detail: String? = null,
        at: Instant = Instant.now(),
    ) {
        // Someone who just left the group no longer has a member entry; their profile still has the name.
        val actorName = localModeDao.member(groupId, actorId)?.displayName
            ?: userDao.get(actorId)?.displayName.orEmpty()
        activityDao.upsertAll(
            listOf(
                ActivityEntity(
                    id = newId(),
                    groupId = groupId,
                    type = type,
                    actorId = actorId,
                    actorName = actorName,
                    targetId = targetId,
                    targetTitle = targetTitle,
                    detail = detail,
                    createdAt = at,
                ),
            ),
        )
    }
}
