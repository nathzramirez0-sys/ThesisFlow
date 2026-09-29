package com.nathzramirez.thesisflow.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import com.nathzramirez.thesisflow.data.di.ApplicationScope
import com.nathzramirez.thesisflow.data.local.dao.ActivityDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.dao.FeedbackDao
import com.nathzramirez.thesisflow.data.local.dao.FileDao
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.TaskCommentDao
import com.nathzramirez.thesisflow.data.local.dao.TaskDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Activity
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Comments
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Feedback
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Files
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Groups
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Members
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Tasks
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Users
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Versions
import com.nathzramirez.thesisflow.data.remote.toActivityEntity
import com.nathzramirez.thesisflow.data.remote.toChapterEntity
import com.nathzramirez.thesisflow.data.remote.toCommentEntity
import com.nathzramirez.thesisflow.data.remote.toFeedbackEntity
import com.nathzramirez.thesisflow.data.remote.toFileEntity
import com.nathzramirez.thesisflow.data.remote.toGroupEntity
import com.nathzramirez.thesisflow.data.remote.toMemberEntity
import com.nathzramirez.thesisflow.data.remote.toTaskRow
import com.nathzramirez.thesisflow.data.remote.toUserEntity
import com.nathzramirez.thesisflow.data.remote.toVersionEntity
import com.nathzramirez.thesisflow.data.remote.uidFlow
import com.nathzramirez.thesisflow.domain.repository.ActivityRepository
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
 * signed-in user's profile, their groups, and for each group its members,
 * chapters, chapter versions, files, tasks, task comments, adviser feedback and
 * recent activity, and writes every change into Room.
 * Firestore raises listeners for local writes too, so Room reflects the user's
 * own edits at once, even offline.
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
    private val chapterDao: ChapterDao,
    private val versionDao: ChapterVersionDao,
    private val fileDao: FileDao,
    private val taskDao: TaskDao,
    private val commentDao: TaskCommentDao,
    private val feedbackDao: FeedbackDao,
    private val activityDao: ActivityDao,
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
        launch { syncGroups(uid) }
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

    /** One listener for the group list; each group's content listeners restart when the list changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun syncGroups(uid: String) {
        firestore.collection(Groups.COLLECTION)
            .whereArrayContains(Groups.MEMBER_IDS, uid)
            .snapshots(MetadataChanges.INCLUDE)
            .retryOnTransientErrors()
            .onEach { snapshot ->
                if (!isCurrentUser(uid)) return@onEach
                val groups = snapshot.documents.mapNotNull { it.toGroupEntity(uid) }
                if (snapshot.metadata.isFromCache) groupDao.upsertAll(groups) else groupDao.replaceAll(groups)
            }
            .map { snapshot -> snapshot.documents.map { it.id }.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { groupIds -> groupIds.flatMap { groupContent(uid, it) }.merge() }
            .catch { Log.w(TAG, "Stopped syncing groups", it) }
            .collect()
    }

    private fun groupContent(uid: String, groupId: String): List<Flow<Unit>> {
        val group = firestore.collection(Groups.COLLECTION).document(groupId)
        return listOf(
            syncQuery(uid, "members of $groupId", group.collection(Members.COLLECTION),
                map = { it.toMemberEntity(groupId) },
                upsert = memberDao::upsertAll,
                replace = { memberDao.replaceForGroup(groupId, it) }),
            syncQuery(uid, "chapters of $groupId", group.collection(Chapters.COLLECTION),
                map = { it.toChapterEntity(groupId) },
                upsert = chapterDao::upsertAll,
                replace = { chapterDao.replaceForGroup(groupId, it) }),
            // Versions live under each chapter; one collection-group query covers them all.
            syncQuery(uid, "versions of $groupId",
                firestore.collectionGroup(Versions.COLLECTION).whereEqualTo(Versions.GROUP_ID, groupId),
                map = { it.toVersionEntity() },
                upsert = versionDao::upsertAll,
                replace = { versionDao.replaceForGroup(groupId, it) }),
            syncQuery(uid, "files of $groupId", group.collection(Files.COLLECTION),
                map = { it.toFileEntity(groupId) },
                upsert = fileDao::upsertAll,
                replace = { fileDao.replaceForGroup(groupId, it) }),
            syncQuery(uid, "tasks of $groupId", group.collection(Tasks.COLLECTION),
                map = { it.toTaskRow(groupId) },
                upsert = taskDao::upsertAll,
                replace = { taskDao.replaceForGroup(groupId, it) }),
            syncQuery(uid, "comments of $groupId",
                firestore.collectionGroup(Comments.COLLECTION).whereEqualTo(Comments.GROUP_ID, groupId),
                map = { it.toCommentEntity() },
                upsert = commentDao::upsertAll,
                replace = { commentDao.replaceForGroup(groupId, it) }),
            syncQuery(uid, "feedback of $groupId",
                firestore.collectionGroup(Feedback.COLLECTION).whereEqualTo(Feedback.GROUP_ID, groupId),
                map = { it.toFeedbackEntity() },
                upsert = feedbackDao::upsertAll,
                replace = { feedbackDao.replaceForGroup(groupId, it) }),
            // Only the latest entries: the feed is for "what's new", and the table stays small.
            syncQuery(uid, "activity of $groupId",
                group.collection(Activity.COLLECTION)
                    .orderBy(Activity.CREATED_AT, Query.Direction.DESCENDING)
                    .limit(ActivityRepository.MAX_CACHED.toLong()),
                map = { it.toActivityEntity(groupId) },
                upsert = activityDao::upsertAll,
                replace = { activityDao.replaceForGroup(groupId, it) }),
        )
    }

    /**
     * Mirrors one query into one table.
     *
     * A snapshot served from the local cache may be incomplete, for example after
     * the cache evicted documents. So only a server-confirmed snapshot may delete
     * rows ([replace]); a cached one can only add or update them ([upsert]).
     * MetadataChanges.INCLUDE makes sure we hear the moment a cached result is confirmed.
     */
    private fun <T> syncQuery(
        uid: String,
        label: String,
        query: Query,
        map: (DocumentSnapshot) -> T?,
        upsert: suspend (List<T>) -> Unit,
        replace: suspend (List<T>) -> Unit,
    ): Flow<Unit> =
        query.snapshots(MetadataChanges.INCLUDE)
            .retryOnTransientErrors()
            .map { snapshot: QuerySnapshot ->
                if (!isCurrentUser(uid)) return@map
                val rows = snapshot.documents.mapNotNull(map)
                if (snapshot.metadata.isFromCache) upsert(rows) else replace(rows)
            }
            // Losing access to one group (e.g. just removed) must not stop the others.
            .catch { Log.w(TAG, "Stopped syncing $label", it) }

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
