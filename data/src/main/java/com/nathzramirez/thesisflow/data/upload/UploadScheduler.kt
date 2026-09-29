package com.nathzramirez.thesisflow.data.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands uploads to WorkManager, which persists them across app restarts and
 * reboots and runs them only when there is a connection.
 */
@Singleton
class UploadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Looked up on use, not at injection, so WorkManager initializes after Hilt is ready.
    private val workManager get() = WorkManager.getInstance(context)

    fun enqueue(fileId: String) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_FILE_ID to fileId))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // One job per file: tapping Retry replaces a stalled attempt instead of running two.
        workManager.enqueueUniqueWork(workName(fileId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(fileId: String) {
        workManager.cancelUniqueWork(workName(fileId))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    private fun workName(fileId: String) = "upload-$fileId"

    private companion object {
        const val TAG = "upload"
    }
}
