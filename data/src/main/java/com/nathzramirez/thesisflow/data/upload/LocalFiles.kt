package com.nathzramirez.thesisflow.data.upload

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where files live on the phone.
 *
 * - Pending uploads go in private files storage: the OS never clears it, and the
 *   copy must survive until the upload succeeds.
 * - Downloaded copies go in the cache: the OS may reclaim them, and they can
 *   always be downloaded again.
 */
@Singleton
class LocalFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val pendingRoot get() = File(context.filesDir, "pending_uploads")
    private val downloadsRoot get() = File(context.cacheDir, "downloads")

    fun pendingDir(fileId: String) = File(pendingRoot, fileId)

    fun downloaded(fileId: String, fileName: String) = File(File(downloadsRoot, fileId), fileName)

    /** Called on sign-out, so the next account on this phone can't open these files. */
    fun deleteAll() {
        pendingRoot.deleteRecursively()
        downloadsRoot.deleteRecursively()
    }

    companion object {
        /** Keeps a picked name safe as a single path segment in Storage and on disk. */
        fun sanitizeName(name: String): String {
            val cleaned = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trimStart('.')
            if (cleaned.isEmpty()) return "file"
            if (cleaned.length <= 100) return cleaned
            val extension = cleaned.substringAfterLast('.', "").take(10)
            val base = cleaned.substringBeforeLast('.').take(100 - extension.length - 1)
            return if (extension.isEmpty()) cleaned.take(100) else "$base.$extension"
        }
    }
}
