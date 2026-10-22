package com.nathzramirez.thesisflow.data.localmode

import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import java.time.Instant

/** A chapter as a group starts it: not started, no drafts, no deadline. */
internal fun newChapterEntity(groupId: String, title: String, order: Int, now: Instant) = ChapterEntity(
    id = newId(),
    groupId = groupId,
    sortOrder = order,
    title = title,
    status = ChapterStatus.NOT_STARTED,
    deadline = null,
    latestVersion = 0,
    updatedAt = now,
    hasPendingWrites = false,
)
