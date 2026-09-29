package com.nathzramirez.thesisflow.domain.validation

/**
 * Invite codes are 6 characters from an alphabet without look-alikes (no 0/O, 1/I),
 * so a code read aloud in class or copied from a photo is typed correctly.
 * The Cloud Function generates codes from the same alphabet.
 */
object InviteCodeFormat {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    /** Accepts "k7qm-2p", " K7QM 2P " and similar, returning "K7QM2P". */
    fun normalize(raw: String): String =
        raw.uppercase().filterNot { it.isWhitespace() || it == '-' }

    fun isValid(normalized: String): Boolean =
        normalized.length == LENGTH && normalized.all { it in ALPHABET }
}
