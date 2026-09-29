package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One activity entry, stored as it arrives. [type] and [detail] stay in their
 * wire form here and are parsed when read, so an entry type this version
 * doesn't know is kept but skipped instead of failing the whole sync.
 */
@Entity(tableName = "activity", indices = [Index("groupId", "createdAt")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val type: String,
    val actorId: String,
    val actorName: String,
    val targetId: String?,
    val targetTitle: String,
    val detail: String?,
    val createdAt: Instant?,
)
