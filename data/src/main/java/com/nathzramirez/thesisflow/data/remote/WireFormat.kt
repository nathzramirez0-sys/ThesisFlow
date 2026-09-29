package com.nathzramirez.thesisflow.data.remote

import com.nathzramirez.thesisflow.domain.model.Role

/*
 * Enums are stored in Firestore as fixed lowercase strings, never as Kotlin's
 * enum names, so renaming a constant in code can't break documents already saved.
 */

internal fun Role.toWire(): String = when (this) {
    Role.LEADER -> "leader"
    Role.MEMBER -> "member"
    Role.ADVISER -> "adviser"
}

internal fun roleFromWire(value: String?): Role? = when (value) {
    "leader" -> Role.LEADER
    "member" -> Role.MEMBER
    "adviser" -> Role.ADVISER
    else -> null
}
