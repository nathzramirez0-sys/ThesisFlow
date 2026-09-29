package com.nathzramirez.thesisflow.data.remote

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Users
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes `users/{uid}`. Shared by the auth and profile repositories. */
@Singleton
internal class UserRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun document(uid: String) = firestore.collection(Users.COLLECTION).document(uid)

    /**
     * Returns the user's profile, creating it on first sign-in. Google accounts
     * arrive with a name and photo; email accounts fill theirs in during onboarding.
     */
    suspend fun fetchOrCreate(user: FirebaseUser): UserEntity {
        document(user.uid).get().await().toUserEntity()?.let { return it }

        val profile = UserEntity(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty().take(50),
            photoUrl = user.photoUrl?.toString(),
            course = "",
            school = "",
            onboardingComplete = false,
        )
        document(user.uid).set(
            mapOf(
                Users.EMAIL to profile.email,
                Users.DISPLAY_NAME to profile.displayName,
                Users.PHOTO_URL to profile.photoUrl,
                Users.COURSE to profile.course,
                Users.SCHOOL to profile.school,
                Users.ONBOARDING_COMPLETE to false,
                Users.CREATED_AT to FieldValue.serverTimestamp(),
                Users.UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).awaitOrQueued()
        return profile
    }

    suspend fun updateProfile(uid: String, displayName: String, course: String, school: String) {
        document(uid).update(
            mapOf(
                Users.DISPLAY_NAME to displayName,
                Users.COURSE to course,
                Users.SCHOOL to school,
                Users.ONBOARDING_COMPLETE to true,
                Users.UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).awaitOrQueued()
    }
}
