package com.nathzramirez.thesisflow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.UserEntity

/**
 * Local read model. Firestore is the source of truth; every row here can be
 * rebuilt from it, which is why schema changes may drop and re-sync the cache.
 */
@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        MemberEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ThesisFlowDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
}
