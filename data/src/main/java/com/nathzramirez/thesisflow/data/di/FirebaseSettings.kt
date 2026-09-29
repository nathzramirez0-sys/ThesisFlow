package com.nathzramirez.thesisflow.data.di

/**
 * Backend settings the app module supplies from BuildConfig, so the data layer
 * never reads build flags itself.
 *
 * @property useEmulators connect to the Firebase Local Emulator Suite instead of production.
 * @property emulatorHost host of the emulators as seen from the device (10.0.2.2 on the Android emulator).
 * @property functionsRegion must match the region the Cloud Functions are deployed to.
 */
data class FirebaseSettings(
    val useEmulators: Boolean,
    val emulatorHost: String,
    val functionsRegion: String,
)
