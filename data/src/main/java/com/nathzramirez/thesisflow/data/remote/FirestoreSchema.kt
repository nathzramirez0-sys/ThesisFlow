package com.nathzramirez.thesisflow.data.remote

/**
 * Collection and field names in one place. The Firestore rules and the Cloud
 * Functions use the same names, so a rename starts here and is searched for there.
 */
internal object FirestoreSchema {

    object Users {
        const val COLLECTION = "users"
        const val EMAIL = "email"
        const val DISPLAY_NAME = "displayName"
        const val PHOTO_URL = "photoUrl"
        const val COURSE = "course"
        const val SCHOOL = "school"
        const val ONBOARDING_COMPLETE = "onboardingComplete"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }

    object Groups {
        const val COLLECTION = "groups"
        const val NAME = "name"
        const val THESIS_TITLE = "thesisTitle"
        const val COURSE = "course"
        const val SCHOOL = "school"
        const val MEMBER_IDS = "memberIds"
        const val ROLES = "roles"
        const val CREATED_BY = "createdBy"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val PROPOSAL_DEFENSE_AT = "proposalDefenseAt"
        const val FINAL_DEFENSE_AT = "finalDefenseAt"
    }

    /** `groups/{groupId}/members/{uid}` */
    object Members {
        const val COLLECTION = "members"
        const val DISPLAY_NAME = "displayName"
        const val PHOTO_URL = "photoUrl"
        const val ROLE = "role"
        const val JOINED_AT = "joinedAt"
    }

    /** `groups/{groupId}/invites/{role}`, written only by Cloud Functions. */
    object Invites {
        const val COLLECTION = "invites"
        const val CODE = "code"
        const val ROLE = "role"
        const val EXPIRES_AT = "expiresAt"
    }

    object Functions {
        const val JOIN_GROUP = "joinGroup"
        const val CREATE_INVITE = "createInvite"
        const val DELETE_GROUP = "deleteGroup"
    }
}
