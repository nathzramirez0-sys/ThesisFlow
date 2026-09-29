package com.nathzramirez.thesisflow.domain.result

import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.domain.validation.ValidationError

/** Every failure the app knows how to explain to the user. The UI maps each one to a message. */
sealed interface DomainError {
    /** One or more form fields are invalid; the UI shows each message under its field. */
    data class InvalidInput(val fieldErrors: Map<Field, ValidationError>) : DomainError

    data object Network : DomainError
    data object PermissionDenied : DomainError
    data object NotFound : DomainError

    data object InvalidCredentials : DomainError
    data object EmailAlreadyInUse : DomainError
    data object WeakPassword : DomainError
    data object TooManyRequests : DomainError
    data object SignInCancelled : DomainError
    data object NoGoogleAccount : DomainError

    data object InviteInvalid : DomainError
    data object InviteExpired : DomainError
    data object GroupFull : DomainError
    /** The change would leave the group without a leader. */
    data object LastLeader : DomainError

    data object UnsupportedFileType : DomainError
    data object FileTooLarge : DomainError
    /** The picked file is empty or could no longer be read. */
    data object FileUnreadable : DomainError

    data class Unknown(val message: String?) : DomainError
}
