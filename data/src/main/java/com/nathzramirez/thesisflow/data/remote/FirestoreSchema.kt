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
        const val UPDATED_BY = "updatedBy"
        const val PROPOSAL_DEFENSE_AT = "proposalDefenseAt"
        const val FINAL_DEFENSE_AT = "finalDefenseAt"
    }

    /** `users/{uid}/devices/{deviceId}`: where to push to. Read by the Cloud Functions. */
    object Devices {
        const val COLLECTION = "devices"
        const val TOKEN = "token"
        const val PLATFORM = "platform"
        const val PLATFORM_ANDROID = "android"
        const val UPDATED_AT = "updatedAt"
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

    /** `groups/{groupId}/chapters/{chapterId}` */
    object Chapters {
        const val COLLECTION = "chapters"
        const val TITLE = "title"
        const val ORDER = "order"
        const val STATUS = "status"
        const val DEADLINE = "deadline"
        const val LATEST_VERSION = "latestVersion"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY = "updatedBy"
    }

    /**
     * `groups/{groupId}/chapters/{chapterId}/versions/{versionNumber}`. The group and
     * chapter ids are repeated inside, so one collection-group query per group can
     * sync every chapter's history.
     */
    object Versions {
        const val COLLECTION = "versions"
        const val GROUP_ID = "groupId"
        const val CHAPTER_ID = "chapterId"
        const val VERSION_NUMBER = "versionNumber"
        const val FILE_ID = "fileId"
        const val NOTE = "note"
        const val UPLOADED_BY = "uploadedBy"
        const val UPLOADED_AT = "uploadedAt"
    }

    /** `groups/{groupId}/files/{fileId}` */
    object Files {
        const val COLLECTION = "files"
        const val NAME = "name"
        const val MIME_TYPE = "mimeType"
        const val SIZE_BYTES = "sizeBytes"
        const val STORAGE_PATH = "storagePath"
        const val KIND = "kind"
        const val CHAPTER_ID = "chapterId"
        const val TASK_ID = "taskId"
        const val FEEDBACK_ID = "feedbackId"
        const val UPLOADED_BY = "uploadedBy"
        const val UPLOADED_AT = "uploadedAt"
    }

    /** `groups/{groupId}/tasks/{taskId}` */
    object Tasks {
        const val COLLECTION = "tasks"
        const val GROUP_ID = "groupId"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val STATUS = "status"
        const val PRIORITY = "priority"
        const val DUE_AT = "dueAt"
        const val CHAPTER_ID = "chapterId"
        const val ASSIGNEE_IDS = "assigneeIds"
        const val CREATED_BY = "createdBy"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY = "updatedBy"
        const val COMPLETED_AT = "completedAt"
        const val COMPLETED_BY = "completedBy"
    }

    /**
     * `groups/{groupId}/tasks/{taskId}/comments/{commentId}`. Like versions, the ids
     * are repeated inside so one collection-group query syncs a group's comments.
     */
    object Comments {
        const val COLLECTION = "comments"
        const val GROUP_ID = "groupId"
        const val TASK_ID = "taskId"
        const val BODY = "body"
        const val AUTHOR_ID = "authorId"
        const val AUTHOR_NAME = "authorName"
        const val CREATED_AT = "createdAt"
    }

    /**
     * `groups/{groupId}/chapters/{chapterId}/feedback/{feedbackId}`. Like versions,
     * the ids are repeated inside so one collection-group query syncs a group's feedback.
     */
    object Feedback {
        const val COLLECTION = "feedback"
        const val GROUP_ID = "groupId"
        const val CHAPTER_ID = "chapterId"
        const val BODY = "body"
        const val AUTHOR_ID = "authorId"
        const val AUTHOR_NAME = "authorName"
        const val AUTHOR_ROLE = "authorRole"
        const val VERSION_NUMBER = "versionNumber"
        const val RESOLVED = "resolved"
        const val RESOLVED_BY = "resolvedBy"
        const val RESOLVED_AT = "resolvedAt"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY = "updatedBy"
    }

    /** `groups/{groupId}/activity/{activityId}`, written only by Cloud Functions. */
    object Activity {
        const val COLLECTION = "activity"
        const val TYPE = "type"
        const val ACTOR_ID = "actorId"
        const val ACTOR_NAME = "actorName"
        const val TARGET_ID = "targetId"
        const val TARGET_TITLE = "targetTitle"
        const val DETAIL = "detail"
        const val CREATED_AT = "createdAt"
    }

    /** Cloud Storage layout; storage.rules matches the same path. */
    object Storage {
        const val METADATA_UPLOADED_BY = "uploadedBy"
        const val METADATA_KIND = "kind"

        fun filePath(groupId: String, fileId: String, fileName: String) =
            "groups/$groupId/files/$fileId/$fileName"
    }

    object Functions {
        const val JOIN_GROUP = "joinGroup"
        const val CREATE_INVITE = "createInvite"
        const val DELETE_GROUP = "deleteGroup"
    }
}
