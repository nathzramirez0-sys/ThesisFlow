package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Entity
import com.nathzramirez.thesisflow.domain.model.Role
import java.time.Instant

/**
 * No foreign key to [GroupEntity]: listeners deliver documents in any order, so a
 * member can arrive before its group. Orphans are removed when groups are reconciled.
 */
@Entity(tableName = "members", primaryKeys = ["groupId", "uid"])
data class MemberEntity(
    val groupId: String,
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
    val role: Role,
    val joinedAt: Instant?,
)
