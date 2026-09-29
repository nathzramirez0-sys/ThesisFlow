package com.nathzramirez.thesisflow.data.remote

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlin.coroutines.cancellation.CancellationException

/** Thrown inside [safeCall] when an operation needs a signed-in user and there is none. */
internal class NotSignedInException : IllegalStateException("No signed-in user")

/**
 * Runs [block] and turns any exception into a [DomainError]. This is the only place
 * Firebase exception types are inspected; nothing above the data layer sees them.
 * Cancellation is rethrown so coroutines still stop when their screen closes.
 */
internal suspend inline fun <T> safeCall(crossinline block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(e.toDomainError().also { if (it is DomainError.Unknown) logUnexpected(e) })
    }

/** Errors we can explain to the user are expected; anything else is logged for debugging. */
internal fun logUnexpected(e: Exception) {
    Log.w("ThesisFlow", "Unexpected failure", e)
}

internal fun Throwable.toDomainError(): DomainError = when (this) {
    is NotSignedInException -> DomainError.PermissionDenied

    // Weak password is a subclass of invalid credentials, so it must be checked first.
    is FirebaseAuthWeakPasswordException -> DomainError.WeakPassword
    is FirebaseAuthUserCollisionException -> DomainError.EmailAlreadyInUse
    is FirebaseAuthInvalidCredentialsException -> DomainError.InvalidCredentials
    is FirebaseAuthInvalidUserException -> DomainError.InvalidCredentials
    is FirebaseTooManyRequestsException -> DomainError.TooManyRequests
    is FirebaseNetworkException -> DomainError.Network

    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        -> DomainError.PermissionDenied
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        -> DomainError.Network
        FirebaseFirestoreException.Code.NOT_FOUND -> DomainError.NotFound
        else -> DomainError.Unknown(message)
    }

    is FirebaseFunctionsException -> reasonFrom(details) ?: when (code) {
        FirebaseFunctionsException.Code.PERMISSION_DENIED,
        FirebaseFunctionsException.Code.UNAUTHENTICATED,
        -> DomainError.PermissionDenied
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        -> DomainError.Network
        FirebaseFunctionsException.Code.NOT_FOUND -> DomainError.NotFound
        else -> DomainError.Unknown(message)
    }

    else -> DomainError.Unknown(message)
}

/**
 * The Cloud Functions send `{ reason: "..." }` in the error details for failures
 * the user can act on. The reason strings are defined in functions/src/errors.ts.
 */
private fun reasonFrom(details: Any?): DomainError? =
    when ((details as? Map<*, *>)?.get("reason")) {
        "INVITE_INVALID" -> DomainError.InviteInvalid
        "INVITE_EXPIRED" -> DomainError.InviteExpired
        "GROUP_FULL" -> DomainError.GroupFull
        "LAST_LEADER" -> DomainError.LastLeader
        else -> null
    }
