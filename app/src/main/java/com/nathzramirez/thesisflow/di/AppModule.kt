package com.nathzramirez.thesisflow.di

import com.nathzramirez.thesisflow.BuildConfig
import com.nathzramirez.thesisflow.data.di.FirebaseSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Build flags are read here once and handed to the data layer as plain values. */
    @Provides
    @Singleton
    fun provideFirebaseSettings(): FirebaseSettings = FirebaseSettings(
        useEmulators = BuildConfig.USE_EMULATORS,
        emulatorHost = BuildConfig.EMULATOR_HOST,
        functionsRegion = BuildConfig.FUNCTIONS_REGION,
    )
}
