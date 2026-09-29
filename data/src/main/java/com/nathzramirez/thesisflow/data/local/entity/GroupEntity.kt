package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nathzramirez.thesisflow.domain.model.Role
import java.time.Instant

/**
 * A group the signed-in user belongs to. [myRole] and [memberCount] are derived from
 * the Firestore `roles` map and `memberIds` array when the document is synced.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thesisTitle: String,
    val course: String,
    val school: String,
    val myRole: Role,
    val memberCount: Int,
    val proposalDefenseAt: Instant?,
    val finalDefenseAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
