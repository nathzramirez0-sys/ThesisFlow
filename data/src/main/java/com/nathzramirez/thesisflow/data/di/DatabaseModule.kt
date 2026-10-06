package com.nathzramirez.thesisflow.data.di

import android.content.Context
import androidx.room.Room
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.ActivityDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.dao.FeedbackDao
import com.nathzramirez.thesisflow.data.local.dao.FileDao
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.PendingUploadDao
import com.nathzramirez.thesisflow.data.local.dao.SentReminderDao
import com.nathzramirez.thesisflow.data.local.dao.TaskCommentDao
import com.nathzramirez.thesisflow.data.local.dao.TaskDao
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
     * Upgrades run the auto-migrations declared on the database. Only a downgrade
     * (installing an older build over a newer one) may drop tables.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ThesisFlowDatabase =
        Room.databaseBuilder(context, ThesisFlowDatabase::class.java, "thesisflow.db")
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideUserDao(database: ThesisFlowDatabase): UserDao = database.userDao()

    @Provides
    fun provideGroupDao(database: ThesisFlowDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideMemberDao(database: ThesisFlowDatabase): MemberDao = database.memberDao()

    @Provides
    fun provideChapterDao(database: ThesisFlowDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideChapterVersionDao(database: ThesisFlowDatabase): ChapterVersionDao = database.chapterVersionDao()

    @Provides
    fun provideFileDao(database: ThesisFlowDatabase): FileDao = database.fileDao()

    @Provides
    fun providePendingUploadDao(database: ThesisFlowDatabase): PendingUploadDao = database.pendingUploadDao()

    @Provides
    fun provideTaskDao(database: ThesisFlowDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideTaskCommentDao(database: ThesisFlowDatabase): TaskCommentDao = database.taskCommentDao()

    @Provides
    fun provideFeedbackDao(database: ThesisFlowDatabase): FeedbackDao = database.feedbackDao()

    @Provides
    fun provideActivityDao(database: ThesisFlowDatabase): ActivityDao = database.activityDao()

    @Provides
    fun provideSentReminderDao(database: ThesisFlowDatabase): SentReminderDao = database.sentReminderDao()
}
