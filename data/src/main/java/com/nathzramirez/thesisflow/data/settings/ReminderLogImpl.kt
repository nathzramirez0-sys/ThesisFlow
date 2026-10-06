package com.nathzramirez.thesisflow.data.settings

import com.nathzramirez.thesisflow.data.local.dao.SentReminderDao
import com.nathzramirez.thesisflow.data.local.entity.SentReminderEntity
import com.nathzramirez.thesisflow.domain.repository.ReminderLog
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** In Room, so it is cleared with everything else when the account signs out. */
@Singleton
internal class ReminderLogImpl @Inject constructor(
    private val dao: SentReminderDao,
) : ReminderLog {

    override suspend fun sentKeys(keys: Collection<String>): Set<String> =
        // SQLite caps the number of query parameters; a day's reminders are far below it.
        keys.chunked(MAX_KEYS_PER_QUERY).flatMap { dao.existing(it) }.toSet()

    override suspend fun markSent(keys: Collection<String>) {
        val now = Instant.now()
        dao.insertAll(keys.map { SentReminderEntity(it, now) })
    }

    override suspend fun prune(days: Long) {
        dao.deleteBefore(Instant.now().minus(Duration.ofDays(days)))
    }

    private companion object {
        const val MAX_KEYS_PER_QUERY = 500
    }
}
