package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.remote.FileUnreadableException
import com.nathzramirez.thesisflow.data.remote.NotSignedInException
import com.nathzramirez.thesisflow.data.remote.logUnexpected
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** A failure the local repositories can explain to the user, such as an expired invite code. */
internal class LocalFailure(val error: DomainError) : Exception(error.toString())

/**
 * The local build's counterpart to safeCall: runs [block] and turns what it
 * throws into a [DomainError]. Cancellation is rethrown so coroutines still
 * stop when their screen closes.
 */
internal suspend inline fun <T> localCall(crossinline block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: LocalFailure) {
        AppResult.Failure(e.error)
    } catch (e: NotSignedInException) {
        AppResult.Failure(DomainError.PermissionDenied)
    } catch (e: FileUnreadableException) {
        AppResult.Failure(DomainError.FileUnreadable)
    } catch (e: Exception) {
        logUnexpected(e)
        AppResult.Failure(DomainError.Unknown(e.message))
    }

/** A random id, the same length as the ones Firestore makes. */
internal fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(20)
