package com.nathzramirez.thesisflow.data.remote

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The signed-in user's uid, or null when signed out. Emits the current value on collection. */
internal fun FirebaseAuth.uidFlow(): Flow<String?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}.distinctUntilChanged()

internal fun FirebaseAuth.requireUid(): String = currentUser?.uid ?: throw NotSignedInException()

/**
 * Waits for a Firestore write, but not forever.
 *
 * Firestore applies a write to its local cache at once, and the Task only completes
 * when the server confirms it. Offline, that can take hours. So we wait briefly:
 * when online, real failures (like a rules rejection) still reach the user; when
 * offline, the write is already queued and will sync on reconnect, so the
 * operation is treated as accepted.
 */
internal suspend fun Task<Void>.awaitOrQueued(timeout: Duration = 3.seconds) {
    withTimeoutOrNull(timeout) { await() }
}
