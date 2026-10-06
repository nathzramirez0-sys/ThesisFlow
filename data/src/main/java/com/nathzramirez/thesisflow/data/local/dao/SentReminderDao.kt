package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nathzramirez.thesisflow.data.local.entity.SentReminderEntity
import java.time.Instant

@Dao
interface SentReminderDao {
    @Query("SELECT reminderKey FROM sent_reminders WHERE reminderKey IN (:keys)")
    suspend fun existing(keys: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reminders: List<SentReminderEntity>)

    @Query("DELETE FROM sent_reminders WHERE sentAt < :before")
    suspend fun deleteBefore(before: Instant)
}
