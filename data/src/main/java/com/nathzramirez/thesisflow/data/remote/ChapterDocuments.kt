package com.nathzramirez.thesisflow.data.remote

import com.google.firebase.firestore.FieldValue
import com.nathzramirez.thesisflow.data.remote.FirestoreSchema.Chapters
import com.nathzramirez.thesisflow.domain.model.ChapterStatus

/**
 * The fields of a new chapter, shared by "add chapter" and group creation so
 * both write exactly what the create rule in firestore.rules expects.
 */
internal fun newChapterFields(title: String, order: Int, uid: String): Map<String, Any?> = mapOf(
    Chapters.TITLE to title,
    Chapters.ORDER to order,
    Chapters.STATUS to ChapterStatus.NOT_STARTED.toWire(),
    Chapters.DEADLINE to null,
    Chapters.LATEST_VERSION to 0,
    Chapters.UPDATED_AT to FieldValue.serverTimestamp(),
    Chapters.UPDATED_BY to uid,
)
