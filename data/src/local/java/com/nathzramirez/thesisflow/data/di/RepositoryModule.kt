package com.nathzramirez.thesisflow.data.di

import com.nathzramirez.thesisflow.data.localmode.AlwaysOnlineNetworkMonitor
import com.nathzramirez.thesisflow.data.localmode.LocalAuthRepository
import com.nathzramirez.thesisflow.data.localmode.LocalBackgroundServices
import com.nathzramirez.thesisflow.data.localmode.LocalChapterRepository
import com.nathzramirez.thesisflow.data.localmode.LocalFeedbackRepository
import com.nathzramirez.thesisflow.data.localmode.LocalFileRepository
import com.nathzramirez.thesisflow.data.localmode.LocalGroupRepository
import com.nathzramirez.thesisflow.data.localmode.LocalPeopleRepository
import com.nathzramirez.thesisflow.data.localmode.LocalTaskRepository
import com.nathzramirez.thesisflow.data.localmode.LocalUserRepository
import com.nathzramirez.thesisflow.data.repository.ActivityRepositoryImpl
import com.nathzramirez.thesisflow.data.settings.ReminderLogImpl
import com.nathzramirez.thesisflow.data.settings.SettingsRepositoryImpl
import com.nathzramirez.thesisflow.data.sync.BackgroundServices
import com.nathzramirez.thesisflow.domain.repository.ActivityRepository
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.FileRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.NetworkMonitor
import com.nathzramirez.thesisflow.domain.repository.PeopleRepository
import com.nathzramirez.thesisflow.domain.repository.ReminderLog
import com.nathzramirez.thesisflow.domain.repository.SettingsRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The local build's bindings: every repository reads and writes Room on this
 * phone, and nothing talks to Firebase. The activity feed, settings and the
 * reminder log already worked from local storage, so they are shared with the
 * cloud build. Screens and use cases see the same domain interfaces either way.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: LocalAuthRepository): AuthRepository

    @Binds
    abstract fun bindUserRepository(impl: LocalUserRepository): UserRepository

    @Binds
    abstract fun bindPeopleRepository(impl: LocalPeopleRepository): PeopleRepository

    @Binds
    abstract fun bindGroupRepository(impl: LocalGroupRepository): GroupRepository

    @Binds
    abstract fun bindChapterRepository(impl: LocalChapterRepository): ChapterRepository

    @Binds
    abstract fun bindFileRepository(impl: LocalFileRepository): FileRepository

    @Binds
    abstract fun bindTaskRepository(impl: LocalTaskRepository): TaskRepository

    @Binds
    abstract fun bindFeedbackRepository(impl: LocalFeedbackRepository): FeedbackRepository

    @Binds
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindReminderLog(impl: ReminderLogImpl): ReminderLog

    @Binds
    abstract fun bindNetworkMonitor(impl: AlwaysOnlineNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun bindBackgroundServices(impl: LocalBackgroundServices): BackgroundServices
}
