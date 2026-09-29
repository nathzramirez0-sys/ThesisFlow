package com.nathzramirez.thesisflow.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import com.nathzramirez.thesisflow.data.di.ApplicationScope
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Members
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Users
import com.nathzramirez.thesisflow.data.remote.toGroupEntity
import com.nathzramirez.thesisflow.data.remote.toMemberEntity
import com.nathzramirez.thesisflow.data.remote.toUserEntity
import com.nathzramirez.thesisflow.data.remote.uidFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Copies Firestore into Room while the app is in the foreground.
 *
 * The UI only ever reads Room. This class keeps snapshot listeners open for the
 * signed-in user's profile, their groups, and each group's members, and writes
 * every change into Room. Firestore raises listeners for local writes too, so
 * Room reflects the user's own edits at once, even offline.
 *
 * Listeners stop when the app goes to the background or the user signs out,
 * and restart when it comes back.
 */
@Singleton
class FirestoreSyncManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val groupDao: GroupDao,
    private val memberDao: MemberDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start(isAppInForeground: Flow<Boolean>) {
        if (job != null) return
        job = scope.launch {
            combine(auth.uidFlow(), isAppInForeground) { uid, foreground -> uid.takeIf { foreground } }
                .distinctUntilChanged()
                .collectLatest { uid -> if (uid != null) syncAccount(uid) }
        }
    }

    private suspend fun syncAccount(uid: String) = coroutineScope {
        launch { syncProfile(uid) }
        launch { syncGroupsAndMembers(uid) }
    }

    private suspend fun syncProfile(uid: String) {
        firestore.collection(Users.COLLECTION).document(uid)
            .snapshots()
            .retryOnTransientErrors()
            .catch { Log.w(TAG, "Stopped syncing the profile", it) }
            .collect { snapshot ->
                val profile = snapshot.toUserEntity() ?: return@collect
                if (isCurrentUser(uid)) userDao.upsert(profile)
            }
    }

    /** One listener for the group list, plus one members listener per group, restarted when the list changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun syncGroupsAndMembers(uid: String) {
        firestore.collection(Groups.COLLECTION)
            .whereArrayContains(Groups.MEMBER_IDS, uid)
            // INCLUDE so we also hear when a cached result is confirmed by the server,
            // which is the moment it becomes safe to delete stale rows.
            .snapshots(MetadataChanges.INCLUDE)
            .retryOnTransientErrors()
            .onEach { snapshot -> writeGroups(uid, snapshot) }
            .map { snapshot -> snapshot.documents.map { it.id }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { groupIds -> groupIds.map { groupId -> syncMembers(uid, groupId) }.merge() }
            .catch { Log.w(TAG, "Stopped syncing groups", it) }
            .collect()
    }

    private fun syncMembers(uid: String, groupId: String): Flow<Unit> =
        firestore.collection(Groups.COLLECTION).document(groupId)
            .collection(Members.COLLECTION)
            .snapshots(MetadataChanges.INCLUDE)
            .retryOnTransientErrors()
            .map { snapshot -> writeMembers(uid, groupId, snapshot) }
            // Losing access to one group (e.g. just removed) must not stop the others.
            .catch { Log.w(TAG, "Stopped syncing members of $groupId", it) }

    /**
     * A snapshot served from the local cache may be incomplete, for example after
     * the cache evicted documents. So only a server-confirmed snapshot may delete
     * rows; a cached one can only add or update them.
     */
    private suspend fun writeGroups(uid: String, snapshot: QuerySnapshot) {
        if (!isCurrentUser(uid)) return
        val groups = snapshot.documents.mapNotNull { it.toGroupEntity(uid) }
        if (snapshot.metadata.isFromCache) groupDao.upsertAll(groups) else groupDao.replaceAll(groups)
    }

    private suspend fun writeMembers(uid: String, groupId: String, snapshot: QuerySnapshot) {
        if (!isCurrentUser(uid)) return
        val members = snapshot.documents.mapNotNull { it.toMemberEntity(groupId) }
        if (snapshot.metadata.isFromCache) memberDao.upsertAll(members) else memberDao.replaceForGroup(groupId, members)
    }

    /** Guards against a late callback from the previous account writing after sign-out. */
    private fun isCurrentUser(uid: String) = auth.currentUser?.uid == uid

    /**
     * Reconnects with exponential backoff (1s, 2s, 4s … capped at 1 min) after
     * transient failures. Permission errors are permanent and end the stream.
     */
    private fun <T> Flow<T>.retryOnTransientErrors(): Flow<T> = retryWhen { cause, attempt ->
        val permanent = cause is FirebaseFirestoreException && cause.code in PERMANENT_ERRORS
        if (permanent) return@retryWhen false
        delay(min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS shl min(attempt.toInt(), 6)))
        true
    }

    private companion object {
        const val TAG = "FirestoreSync"
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        val PERMANENT_ERRORS = setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT,
        )
    }
}
