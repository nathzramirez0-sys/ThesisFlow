package com.nathzramirez.thesisflow.domain.repository

/** Which reminders were already shown, so each one appears once. */
interface ReminderLog {
    /** The subset of [keys] already shown. */
    suspend fun sentKeys(keys: Collection<String>): Set<String>

    suspend fun markSent(keys: Collection<String>)

    /** Forgets entries older than [days] days; their due dates are long past. */
    suspend fun prune(days: Long)
}
