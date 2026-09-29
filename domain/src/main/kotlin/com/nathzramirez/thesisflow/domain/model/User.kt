package com.nathzramirez.thesisflow.domain.model

data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val course: String,
    val school: String,
    val onboardingComplete: Boolean,
)
