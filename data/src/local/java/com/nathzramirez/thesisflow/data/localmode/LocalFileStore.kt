package com.nathzramirez.thesisflow.data.localmode

import android.content.Context
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Where the local build keeps uploaded files: in app storage, which the system
 * never clears on its own (unlike the cloud build's download cache). One folder
 * per file id, like the Storage path, so two files with the same name never clash.
 */
internal class LocalFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val root get() = File(context.filesDir, "local_files")

    fun fileFor(fileId: String, name: String) = File(File(root, fileId), name)

    /** The relative path recorded on the file's row, in the same shape as the cloud build's Storage path. */
    fun storagePath(groupId: String, fileId: String, name: String) = "groups/$groupId/files/$fileId/$name"

    fun delete(files: List<FileEntity>) {
        files.forEach { File(root, it.id).deleteRecursively() }
    }
}
