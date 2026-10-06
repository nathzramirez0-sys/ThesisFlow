package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** A reminder already shown on this phone; see ReminderPlanner for how keys are built. */
@Entity(tableName = "sent_reminders", indices = [Index("sentAt")])
data class SentReminderEntity(
    @PrimaryKey val reminderKey: String,
    val sentAt: Instant,
)
