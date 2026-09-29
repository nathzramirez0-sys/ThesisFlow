package com.nathzramirez.thesisflow.domain.validation

/**
 * Pure validation rules shared by every form. Each function returns null when the
 * value is valid. Limits match the Firestore security rules, so anything that
 * passes here is also accepted by the server.
 */
object Validators {
    const val PASSWORD_MIN_LENGTH = 8
    const val DISPLAY_NAME_MIN = 2
    const val DISPLAY_NAME_MAX = 50
    const val COURSE_MAX = 80
    const val SCHOOL_MAX = 100
    const val GROUP_NAME_MIN = 3
    const val GROUP_NAME_MAX = 60
    const val THESIS_TITLE_MAX = 200

    private val emailRegex = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun email(value: String): ValidationError? = when {
        value.isBlank() -> ValidationError.REQUIRED
        !emailRegex.matches(value.trim()) -> ValidationError.INVALID_EMAIL
        else -> null
    }

    fun newPassword(value: String): ValidationError? = when {
        value.isEmpty() -> ValidationError.REQUIRED
        value.length < PASSWORD_MIN_LENGTH -> ValidationError.PASSWORD_TOO_SHORT
        value.none(Char::isLetter) || value.none(Char::isDigit) ->
            ValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT
        else -> null
    }

    fun confirmPassword(password: String, confirmation: String): ValidationError? = when {
        confirmation.isEmpty() -> ValidationError.REQUIRED
        confirmation != password -> ValidationError.PASSWORDS_DO_NOT_MATCH
        else -> null
    }

    /** Sign-in only checks presence; the strength rules apply when the password is created. */
    fun existingPassword(value: String): ValidationError? =
        if (value.isEmpty()) ValidationError.REQUIRED else null

    fun displayName(value: String): ValidationError? =
        text(value, required = true, min = DISPLAY_NAME_MIN, max = DISPLAY_NAME_MAX)

    fun course(value: String): ValidationError? = text(value, required = true, max = COURSE_MAX)

    fun school(value: String): ValidationError? = text(value, required = true, max = SCHOOL_MAX)

    fun groupName(value: String): ValidationError? =
        text(value, required = true, min = GROUP_NAME_MIN, max = GROUP_NAME_MAX)

    fun thesisTitle(value: String): ValidationError? =
        text(value, required = false, max = THESIS_TITLE_MAX)

    fun inviteCode(normalized: String): ValidationError? = when {
        normalized.isEmpty() -> ValidationError.REQUIRED
        !InviteCodeFormat.isValid(normalized) -> ValidationError.INVALID_INVITE_CODE
        else -> null
    }

    private fun text(value: String, required: Boolean, min: Int = 0, max: Int): ValidationError? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> if (required) ValidationError.REQUIRED else null
            trimmed.length < min -> ValidationError.TOO_SHORT
            trimmed.length > max -> ValidationError.TOO_LONG
            else -> null
        }
    }
}

/** Collects the non-null errors into a map, so a form can show all of them at once. */
fun fieldErrorsOf(vararg checks: Pair<Field, ValidationError?>): Map<Field, ValidationError> =
    checks.mapNotNull { (field, error) -> error?.let { field to it } }.toMap()
