package com.nathzramirez.thesisflow.feature.chapters.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens a downloaded file in another app. The file is shared through a
 * FileProvider content URI with a one-off read grant, never a raw file path.
 */
object FileOpener {

    /** Returns false when no installed app can open this type of file. */
    fun open(context: Context, path: String, mimeType: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
