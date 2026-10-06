package com.nathzramirez.thesisflow.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One instance of each Firebase client per process. The emulator switch must run
 * before the first call on each client, which a singleton provider guarantees.
 *
 * Firestore's persistent disk cache is on by default on Android. It stays on:
 * it is what keeps queued offline writes alive across app restarts.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8080
    private const val FUNCTIONS_EMULATOR_PORT = 5001
    private const val STORAGE_EMULATOR_PORT = 9199

    @Provides
    @Singleton
    fun provideFirebaseAuth(settings: FirebaseSettings): FirebaseAuth =
        FirebaseAuth.getInstance().apply {
            if (settings.useEmulators) useEmulator(settings.emulatorHost, AUTH_EMULATOR_PORT)
        }

    @Provides
    @Singleton
    fun provideFirestore(settings: FirebaseSettings): FirebaseFirestore =
        FirebaseFirestore.getInstance().apply {
            if (settings.useEmulators) useEmulator(settings.emulatorHost, FIRESTORE_EMULATOR_PORT)
        }

    @Provides
    @Singleton
    fun provideFunctions(settings: FirebaseSettings): FirebaseFunctions =
        FirebaseFunctions.getInstance(settings.functionsRegion).apply {
            if (settings.useEmulators) useEmulator(settings.emulatorHost, FUNCTIONS_EMULATOR_PORT)
        }

    /** FCM has no emulator; with the demo config, token requests simply fail and pushes stay off. */
    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideStorage(settings: FirebaseSettings): FirebaseStorage =
        FirebaseStorage.getInstance().apply {
            if (settings.useEmulators) useEmulator(settings.emulatorHost, STORAGE_EMULATOR_PORT)
        }
}
