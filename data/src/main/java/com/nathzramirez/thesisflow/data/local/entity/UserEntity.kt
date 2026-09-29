package com.nathzramirez.thesisflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The signed-in user's own profile. Other people's names come from [MemberEntity]. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val course: String,
    val school: String,
    val onboardingComplete: Boolean,
)
