package com.nathzramirez.thesisflow.data.di

import android.content.Context
import androidx.room.Room
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Room is a cache of Firestore, so on a schema change it is safe to drop the
     * tables and let the listeners fill them again. Offline writes are unaffected:
     * they are queued in Firestore's own cache, not in Room.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ThesisFlowDatabase =
        Room.databaseBuilder(context, ThesisFlowDatabase::class.java, "thesisflow.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideUserDao(database: ThesisFlowDatabase): UserDao = database.userDao()

    @Provides
    fun provideGroupDao(database: ThesisFlowDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideMemberDao(database: ThesisFlowDatabase): MemberDao = database.memberDao()
}
