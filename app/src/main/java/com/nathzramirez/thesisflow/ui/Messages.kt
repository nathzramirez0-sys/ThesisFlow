package com.nathzramirez.thesisflow.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError
import com.nathzramirez.thesisflow.domain.validation.Validators

/*
 * The only place domain errors become words. ViewModels keep DomainError and
 * ValidationError values in their state, which keeps them free of Android
 * resources and easy to unit test.
 */

@StringRes
fun DomainError.messageRes(): Int = when (this) {
    is DomainError.InvalidInput -> R.string.error_check_fields
    DomainError.Network -> R.string.error_network
    DomainError.PermissionDenied -> R.string.error_permission
    DomainError.NotFound -> R.string.error_not_found
    DomainError.InvalidCredentials -> R.string.error_invalid_credentials
    DomainError.EmailAlreadyInUse -> R.string.error_email_in_use
    DomainError.WeakPassword -> R.string.error_weak_password
    DomainError.TooManyRequests -> R.string.error_too_many_requests
    DomainError.NoGoogleAccount -> R.string.error_no_google_account
    DomainError.InviteInvalid -> R.string.error_invite_invalid
    DomainError.InviteExpired -> R.string.error_invite_expired
    DomainError.GroupFull -> R.string.error_group_full
    DomainError.LastLeader -> R.string.error_last_leader
    DomainError.UnsupportedFileType -> R.string.error_unsupported_file
    DomainError.FileTooLarge -> R.string.error_file_too_large
    DomainError.FileUnreadable -> R.string.error_file_unreadable
    DomainError.SignInCancelled,
    is DomainError.Unknown,
    -> R.string.error_unknown
}

/** The message under a field, or null when it is valid. */
@Composable
fun Map<Field, ValidationError>.messageFor(field: Field): String? =
    this[field]?.let { validationMessage(field, it) }

@Composable
fun validationMessage(field: Field, error: ValidationError): String = when (error) {
    ValidationError.REQUIRED -> stringResource(requiredMessage(field))
    ValidationError.INVALID_EMAIL -> stringResource(R.string.validation_invalid_email)
    ValidationError.PASSWORD_TOO_SHORT ->
        stringResource(R.string.validation_min_length, Validators.PASSWORD_MIN_LENGTH)
    ValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT -> stringResource(R.string.validation_letter_and_digit)
    ValidationError.PASSWORDS_DO_NOT_MATCH -> stringResource(R.string.validation_passwords_differ)
    ValidationError.TOO_SHORT -> stringResource(R.string.validation_min_length, minLength(field))
    ValidationError.TOO_LONG -> stringResource(R.string.validation_max_length, maxLength(field))
    ValidationError.INVALID_INVITE_CODE -> stringResource(R.string.validation_invite_code)
}

@StringRes
private fun requiredMessage(field: Field): Int = when (field) {
    Field.EMAIL -> R.string.validation_required_email
    Field.PASSWORD -> R.string.validation_required_password
    Field.CONFIRM_PASSWORD -> R.string.validation_required_confirm
    Field.DISPLAY_NAME -> R.string.validation_required_name
    Field.COURSE -> R.string.validation_required_course
    Field.SCHOOL -> R.string.validation_required_school
    Field.GROUP_NAME -> R.string.validation_required_group_name
    Field.INVITE_CODE -> R.string.validation_required_invite_code
    Field.CHAPTER_TITLE -> R.string.validation_required_chapter_title
    Field.TASK_TITLE -> R.string.validation_required_task_title
    Field.COMMENT -> R.string.validation_required_comment
    Field.THESIS_TITLE, Field.VERSION_NOTE, Field.TASK_DESCRIPTION -> R.string.validation_required
}

private fun minLength(field: Field): Int = when (field) {
    Field.DISPLAY_NAME -> Validators.DISPLAY_NAME_MIN
    Field.GROUP_NAME -> Validators.GROUP_NAME_MIN
    else -> 1
}

private fun maxLength(field: Field): Int = when (field) {
    Field.DISPLAY_NAME -> Validators.DISPLAY_NAME_MAX
    Field.COURSE -> Validators.COURSE_MAX
    Field.SCHOOL -> Validators.SCHOOL_MAX
    Field.GROUP_NAME -> Validators.GROUP_NAME_MAX
    Field.THESIS_TITLE -> Validators.THESIS_TITLE_MAX
    Field.CHAPTER_TITLE -> Validators.CHAPTER_TITLE_MAX
    Field.VERSION_NOTE -> Validators.VERSION_NOTE_MAX
    Field.TASK_TITLE -> Validators.TASK_TITLE_MAX
    Field.TASK_DESCRIPTION -> Validators.TASK_DESCRIPTION_MAX
    Field.COMMENT -> Validators.COMMENT_MAX
    else -> Int.MAX_VALUE
}
