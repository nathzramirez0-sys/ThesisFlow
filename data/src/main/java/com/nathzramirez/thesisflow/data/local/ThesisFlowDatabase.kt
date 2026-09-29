package com.nathzramirez.thesisflow.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.dao.FileDao
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.PendingUploadDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.ChapterVersionEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.PendingUploadEntity
import com.nathzramirez.thesisflow.data.local.entity.UserEntity

/**
 * Local read model. Firestore is the source of truth for every table except
 * pending_uploads, which holds files not yet sent. Because that data can't be
 * rebuilt, schema changes use migrations (generated from the exported schemas
 * in data/schemas) rather than dropping the database.
 */
@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        MemberEntity::class,
        ChapterEntity::class,
        ChapterVersionEntity::class,
        FileEntity::class,
        PendingUploadEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class ThesisFlowDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterVersionDao(): ChapterVersionDao
    abstract fun fileDao(): FileDao
    abstract fun pendingUploadDao(): PendingUploadDao
}
