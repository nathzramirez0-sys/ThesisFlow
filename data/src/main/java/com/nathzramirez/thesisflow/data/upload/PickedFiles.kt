package com.nathzramirez.thesisflow.data.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.nathzramirez.thesisflow.data.remote.FileUnreadableException
import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Reads files picked with the system file picker. Both builds use it: the cloud
 * build copies a file aside to upload it, the local build to keep it.
 * Both calls block on disk I/O, so run them off the main thread.
 */
internal class PickedFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** The file's name, type and size, read without copying it. */
    fun inspect(sourceUri: String): LocalFileInfo {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
            // Some providers don't report a size; ask the file itself.
            if (size < 0) size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
        } catch (e: IOException) {
            throw FileUnreadableException(e)
        } catch (e: SecurityException) {
            throw FileUnreadableException(e)
        }
        val fileName = LocalFiles.sanitizeName(name ?: uri.lastPathSegment ?: "file")
        val mimeType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        return LocalFileInfo(name = fileName, mimeType = mimeType, sizeBytes = size)
    }

    /** Copies the picked file to [target]. On failure nothing is left at [target]. */
    fun copy(sourceUri: String, target: File) {
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                ?: throw IOException("Could not open $sourceUri")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (e: IOException) {
            target.delete()
            throw FileUnreadableException(e)
        } catch (e: SecurityException) {
            target.delete()
            throw FileUnreadableException(e)
        }
    }
}
