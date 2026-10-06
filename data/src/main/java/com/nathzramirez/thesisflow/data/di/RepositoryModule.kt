package com.nathzramirez.thesisflow.data.di

import com.nathzramirez.thesisflow.data.repository.ActivityRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.AuthRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.ChapterRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.FeedbackRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.FileRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.GroupRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.TaskRepositoryImpl
import com.nathzramirez.thesisflow.data.repository.UserRepositoryImpl
import com.nathzramirez.thesisflow.data.settings.ReminderLogImpl
import com.nathzramirez.thesisflow.data.settings.SettingsRepositoryImpl
import com.nathzramirez.thesisflow.domain.repository.ActivityRepository
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.ReminderLog
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The implementations are `internal`, so the app module can only reach them
 * through the domain interfaces bound here.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    @Binds
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindReminderLog(impl: ReminderLogImpl): ReminderLog
}
