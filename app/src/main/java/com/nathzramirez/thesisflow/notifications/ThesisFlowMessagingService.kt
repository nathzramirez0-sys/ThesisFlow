package com.nathzramirez.thesisflow.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nathzramirez.thesisflow.data.push.PushPayloads
import com.nathzramirez.thesisflow.data.push.PushTokenRegistrar
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Receives data-only pushes, so this runs whether the app is open or not, and
 * the app (not FCM) decides the wording, channel and screen to open.
 */
@AndroidEntryPoint
class ThesisFlowMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registrar: PushTokenRegistrar

    @Inject
    lateinit var notifier: Notifier

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settings: SettingsRepository

    override fun onNewToken(token: String) {
        registrar.onNewToken(token)
    }

    /**
     * Called on a background thread with a few seconds to finish, so blocking
     * briefly for the signed-in user and the setting is fine here.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val notice = PushPayloads.parse(message.data) ?: return
        val (uid, pushEnabled) = runBlocking {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                val uid = (authRepository.authState.first() as? AuthState.SignedIn)?.uid
                uid to settings.notificationSettings.first().pushEnabled
            }
        } ?: return
        // Meant for whoever used this phone before, or pushes were just turned off: never show it.
        if (notice.recipientId != uid || !pushEnabled) return
        notifier.show(notice)
    }

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 2_000L
    }
}
