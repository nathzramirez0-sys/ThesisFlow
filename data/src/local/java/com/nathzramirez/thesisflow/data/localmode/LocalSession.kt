package com.nathzramirez.thesisflow.data.localmode

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nathzramirez.thesisflow.data.remote.NotSignedInException
import com.nathzramirez.thesisflow.domain.model.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** An invite code, kept on the phone in place of the server's inviteCodes collection. */
internal data class LocalInvite(
    val code: String,
    val groupId: String,
    val role: Role,
    val expiresAt: Instant,
)

/**
 * Who is using the local build right now, and the group invite codes. Choosing
 * a person here is the local build's sign-in. Both live in their own DataStore
 * file, which the app's constructor creates: DataStore allows one instance per
 * file, and this class is a singleton. Tests pass a store of their own.
 */
@Singleton
internal class LocalSession(private val store: DataStore<Preferences>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("local_session") },
        ),
    )

    /** The person using the app, or null while the people list is showing. */
    val currentUid: Flow<String?> = store.data.map { it[CURRENT_UID] }.distinctUntilChanged()

    suspend fun requireUid(): String = currentUid.first() ?: throw NotSignedInException()

    suspend fun switchTo(uid: String?) {
        store.edit { prefs -> if (uid == null) prefs.remove(CURRENT_UID) else prefs[CURRENT_UID] = uid }
    }

    val invites: Flow<List<LocalInvite>> = store.data.map { prefs -> prefs.invites() }

    suspend fun findInvite(code: String): LocalInvite? = store.data.first().invites().find { it.code == code }

    /** Saves [invite] and revokes the group's previous code for the same role, like createInvite does. */
    suspend fun replaceInvite(invite: LocalInvite) {
        store.edit { prefs ->
            prefs.invites()
                .filter { it.groupId == invite.groupId && it.role == invite.role }
                .forEach { prefs.remove(inviteKey(it.code)) }
            prefs[inviteKey(invite.code)] = listOf(invite.groupId, invite.role.name, invite.expiresAt.toEpochMilli())
                .joinToString(SEPARATOR)
        }
    }

    suspend fun deleteInvitesFor(groupId: String) {
        store.edit { prefs ->
            prefs.invites().filter { it.groupId == groupId }.forEach { prefs.remove(inviteKey(it.code)) }
        }
    }

    private fun Preferences.invites(): List<LocalInvite> = asMap().mapNotNull { (key, value) ->
        val code = key.name.removePrefix(INVITE_PREFIX).takeIf { key.name.startsWith(INVITE_PREFIX) }
        val parts = (value as? String)?.split(SEPARATOR)
        if (code == null || parts == null || parts.size != 3) return@mapNotNull null
        val role = Role.entries.find { it.name == parts[1] } ?: return@mapNotNull null
        val expiresAt = parts[2].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
        LocalInvite(code = code, groupId = parts[0], role = role, expiresAt = expiresAt)
    }

    private fun inviteKey(code: String) = stringPreferencesKey(INVITE_PREFIX + code)

    private companion object {
        val CURRENT_UID = stringPreferencesKey("current_uid")
        const val INVITE_PREFIX = "invite."
        const val SEPARATOR = "|"
    }
}
