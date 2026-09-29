package com.nathzramirez.thesisflow.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.ChapterVersionEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.VersionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE groupId = :groupId ORDER BY sortOrder, title")
    fun observeForGroup(groupId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters")
    fun observeAll(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    fun observe(chapterId: String): Flow<ChapterEntity?>

    @Query("SELECT MAX(sortOrder) FROM chapters WHERE groupId = :groupId")
    suspend fun maxOrder(groupId: String): Int?

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun replaceForGroup(groupId: String, chapters: List<ChapterEntity>) {
        deleteForGroup(groupId)
        upsertAll(chapters)
    }

    @Query("DELETE FROM chapters WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface ChapterVersionDao {
    /** Newest first, each with its file and uploader. */
    @Transaction
    @Query("SELECT * FROM chapter_versions WHERE chapterId = :chapterId ORDER BY versionNumber DESC")
    fun observeForChapter(chapterId: String): Flow<List<VersionWithDetails>>

    @Upsert
    suspend fun upsertAll(versions: List<ChapterVersionEntity>)

    @Transaction
    suspend fun replaceForGroup(groupId: String, versions: List<ChapterVersionEntity>) {
        deleteForGroup(groupId)
        upsertAll(versions)
    }

    @Query("DELETE FROM chapter_versions WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE id = :fileId")
    suspend fun get(fileId: String): FileEntity?

    @Upsert
    suspend fun upsertAll(files: List<FileEntity>)

    @Transaction
    suspend fun replaceForGroup(groupId: String, files: List<FileEntity>) {
        deleteForGroup(groupId)
        upsertAll(files)
    }

    @Query("DELETE FROM files WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
