package com.nathzramirez.thesisflow.data.localmode

import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.LocalModeDao
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import javax.inject.Inject

/**
 * What the clean-up Cloud Functions do in the cloud build (functions/src/chapters.ts
 * and groups.ts): deleting something also deletes everything that hangs off it.
 * The rows go in one transaction; the files on disk follow once it has committed.
 */
internal class LocalCleanup @Inject constructor(
    private val database: ThesisFlowDatabase,
    private val localModeDao: LocalModeDao,
    private val groupDao: GroupDao,
    private val fileStore: LocalFileStore,
    private val session: LocalSession,
) {

    /** The chapter, its drafts and its feedback, with every file on either. */
    suspend fun deleteChapter(chapterId: String) = deleteThen {
        localModeDao.filesOfChapter(chapterId).also { files ->
            localModeDao.deleteVersionsOf(chapterId)
            localModeDao.deleteFeedbackOf(chapterId)
            localModeDao.deleteFiles(files.map { it.id })
            localModeDao.deleteChapter(chapterId)
        }
    }

    /** The task, its comments, its assignees and its attachments. */
    suspend fun deleteTask(taskId: String) = deleteThen {
        localModeDao.filesOfTask(taskId).also { files ->
            localModeDao.deleteCommentsOf(taskId)
            localModeDao.deleteAssigneesOf(taskId)
            localModeDao.deleteFiles(files.map { it.id })
            localModeDao.deleteTask(taskId)
        }
    }

    /** The feedback and the files the adviser attached to it. */
    suspend fun deleteFeedback(feedbackId: String) = deleteThen {
        localModeDao.filesOfFeedback(feedbackId).also { files ->
            localModeDao.deleteFiles(files.map { it.id })
            localModeDao.deleteFeedback(feedbackId)
        }
    }

    /** The group and everything in it, its invite codes included. */
    suspend fun deleteGroup(groupId: String) {
        deleteThen {
            localModeDao.filesOfGroup(groupId).also { groupDao.deleteWithContent(groupId) }
        }
        session.deleteInvitesFor(groupId)
    }

    private suspend fun deleteThen(deleteRows: suspend () -> List<FileEntity>) {
        val removed = database.withTransaction { deleteRows() }
        fileStore.delete(removed)
    }
}
