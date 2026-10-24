package com.nathzramirez.thesisflow.data.localmode

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.repository.ActivityRepositoryImpl
import com.nathzramirez.thesisflow.data.upload.PickedFiles
import com.nathzramirez.thesisflow.domain.model.Activity
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.Closeable
import java.io.File
import java.util.UUID

/**
 * The local build's data layer wired the way its Hilt module wires it, over an
 * in-memory database and a session file of its own, so every test starts empty.
 */
internal class LocalModeTestGraph : Closeable {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    val db: ThesisFlowDatabase = Room.inMemoryDatabaseBuilder(context, ThesisFlowDatabase::class.java).build()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // DataStore allows one instance per file, and wants this extension.
    private val sessionFile = File(context.cacheDir, "session-${UUID.randomUUID()}.preferences_pb")
    val session = LocalSession(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { sessionFile }))

    val fileStore = LocalFileStore(context)
    private val activityLog = LocalActivityLog(db.activityDao(), db.localModeDao(), db.userDao())
    private val cleanup = LocalCleanup(db, db.localModeDao(), db.groupDao(), fileStore, session)

    val auth = LocalAuthRepository(session, db.userDao(), db.localModeDao())
    val users = LocalUserRepository(session, db, db.userDao(), db.localModeDao())
    val people = LocalPeopleRepository(
        session,
        db.userDao(),
        db.localModeDao(),
        SampleGroup(
            Dispatchers.IO, db, db.userDao(), db.groupDao(), db.memberDao(), db.chapterDao(),
            db.chapterVersionDao(), db.fileDao(), db.taskDao(), db.taskCommentDao(), db.feedbackDao(),
            activityLog, fileStore,
        ),
    )
    val groups = LocalGroupRepository(
        session, db, db.groupDao(), db.memberDao(), db.chapterDao(), db.userDao(), db.localModeDao(),
        activityLog, cleanup,
    )
    val chapters = LocalChapterRepository(
        session, db.chapterDao(), db.chapterVersionDao(), db.localModeDao(), activityLog, cleanup,
    )
    val tasks = LocalTaskRepository(
        session, db, db.taskDao(), db.taskCommentDao(), db.userDao(), db.localModeDao(), activityLog, cleanup,
    )
    val feedback = LocalFeedbackRepository(
        session, db, db.feedbackDao(), db.chapterDao(), db.localModeDao(), activityLog, cleanup,
    )
    val files = LocalFileRepository(
        Dispatchers.IO, session, db, PickedFiles(context), fileStore, db.fileDao(), db.chapterDao(),
        db.chapterVersionDao(), db.localModeDao(), activityLog,
    )
    val activity = ActivityRepositoryImpl(db.activityDao())

    /** Adds someone the way the people list and onboarding do, and leaves them signed in. */
    suspend fun person(name: String): String {
        people.addPerson().value()
        users.updateProfile(name, "BS Computer Science", "PHINMA University of Pangasinan").value()
        return session.requireUid()
    }

    suspend fun switchTo(uid: String) = people.switchTo(uid).value()

    /** A group led by [leaderName], with everyone else joined through invite codes like the app does. */
    suspend fun groupWith(leaderName: String, vararg others: Pair<String, Role>): Team {
        val leader = person(leaderName)
        val groupId = groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        val uids = others.map { (name, role) ->
            switchTo(leader)
            val code = groups.createInvite(groupId, role).value().code
            person(name).also { groups.joinGroup(code).value() }
        }
        switchTo(leader)
        return Team(groupId, leader, uids)
    }

    suspend fun feed(groupId: String): List<Activity> = activity.observeRecent(groupId).first()

    /** A real file in the test app's cache, picked the way a file:// URI from a picker would be. */
    fun pickableFile(name: String, bytes: Int = 1024): String {
        val file = File(context.cacheDir, "picked-${UUID.randomUUID()}/$name")
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes) { it.toByte() })
        return Uri.fromFile(file).toString()
    }

    override fun close() {
        db.close()
        storeScope.cancel()
        sessionFile.delete()
        File(context.filesDir, "local_files").deleteRecursively()
    }

    data class Team(val groupId: String, val leader: String, val others: List<String>)
}

/** The value, or a test failure naming the error. */
internal fun <T> AppResult<T>.value(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw AssertionError("Expected success, got $error")
}

internal fun AppResult<*>.error(): DomainError = when (this) {
    is AppResult.Success -> throw AssertionError("Expected a failure, got $value")
    is AppResult.Failure -> error
}
