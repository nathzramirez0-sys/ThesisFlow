package com.nathzramirez.thesisflow.data.push

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.nathzramirez.thesisflow.data.di.ApplicationScope
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Devices
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Users
import com.nathzramirez.thesisflow.data.remote.awaitOrQueued
import com.nathzramirez.thesisflow.data.remote.uidFlow
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this phone's FCM token at `users/{uid}/devices/{deviceId}`, where the
 * Cloud Functions look up whom to push to.
 *
 * The document id is a random id made once per installation, not the token
 * itself: when FCM rotates the token, the same document is overwritten instead
 * of leaving a stale one behind. The entry exists only while someone is signed
 * in and has pushes turned on.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val settings: SettingsRepository,
    private val store: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            combine(auth.uidFlow(), settings.notificationSettings.map { it.pushEnabled }) { uid, enabled -> uid to enabled }
                .distinctUntilChanged()
                .collectLatest { (uid, enabled) ->
                    if (uid == null) return@collectLatest
                    if (enabled) register(uid) else unregister(uid)
                }
        }
    }

    /** FCM rotated the token; called from the messaging service, which can't wait for Firestore. */
    fun onNewToken(token: String) {
        scope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            if (!settings.notificationSettings.first().pushEnabled) return@launch
            save(uid, token)
        }
    }

    /**
     * Removes this phone's entry before signing out, while the rules still let the
     * user delete it. Pushes that slip through anyway are dropped on arrival,
     * because each one names its recipient.
     */
    suspend fun unregisterCurrentUser() {
        auth.currentUser?.uid?.let { unregister(it) }
        try {
            // Offline this would wait for the network; sign-out must not.
            withTimeoutOrNull(DELETE_TOKEN_TIMEOUT_MS) { messaging.deleteToken().await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the FCM token", e)
        }
    }

    private suspend fun register(uid: String) {
        try {
            save(uid, messaging.token.await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Play services, no network on first launch, or a demo config with no real project.
            Log.w(TAG, "No push token; this device won't get pushes for now", e)
        }
    }

    private suspend fun save(uid: String, token: String) {
        device(uid).set(
            mapOf(
                Devices.TOKEN to token,
                Devices.PLATFORM to Devices.PLATFORM_ANDROID,
                Devices.UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).awaitOrQueued()
    }

    private suspend fun unregister(uid: String) {
        try {
            device(uid).delete().awaitOrQueued()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove this device's push entry", e)
        }
    }

    private suspend fun device(uid: String) =
        firestore.collection(Users.COLLECTION).document(uid).collection(Devices.COLLECTION).document(deviceId())

    private suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        store.edit { prefs -> if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = UUID.randomUUID().toString() }
        return checkNotNull(store.data.first()[DEVICE_ID])
    }

    private companion object {
        const val TAG = "PushTokens"
        const val DELETE_TOKEN_TIMEOUT_MS = 3_000L
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
