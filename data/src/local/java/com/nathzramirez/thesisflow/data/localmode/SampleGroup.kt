package com.nathzramirez.thesisflow.data.localmode

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.withTransaction
import com.nathzramirez.thesisflow.data.di.IoDispatcher
import com.nathzramirez.thesisflow.data.local.ThesisFlowDatabase
import com.nathzramirez.thesisflow.data.local.dao.ChapterDao
import com.nathzramirez.thesisflow.data.local.dao.ChapterVersionDao
import com.nathzramirez.thesisflow.data.local.dao.FeedbackDao
import com.nathzramirez.thesisflow.data.local.dao.FileDao
import com.nathzramirez.thesisflow.data.local.dao.GroupDao
import com.nathzramirez.thesisflow.data.local.dao.MemberDao
import com.nathzramirez.thesisflow.data.local.dao.TaskCommentDao
import com.nathzramirez.thesisflow.data.local.dao.TaskDao
import com.nathzramirez.thesisflow.data.local.dao.UserDao
import com.nathzramirez.thesisflow.data.local.entity.ChapterEntity
import com.nathzramirez.thesisflow.data.local.entity.ChapterVersionEntity
import com.nathzramirez.thesisflow.data.local.entity.FeedbackEntity
import com.nathzramirez.thesisflow.data.local.entity.FileEntity
import com.nathzramirez.thesisflow.data.local.entity.GroupEntity
import com.nathzramirez.thesisflow.data.local.entity.MemberEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskAssigneeEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskCommentEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskEntity
import com.nathzramirez.thesisflow.data.local.entity.TaskRow
import com.nathzramirez.thesisflow.data.local.entity.UserEntity
import com.nathzramirez.thesisflow.data.remote.ActivityTypes
import com.nathzramirez.thesisflow.data.remote.toWire
import com.nathzramirez.thesisflow.domain.model.ChapterStatus
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.FileKind
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskPriority
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * A sample thesis group for trying the local build or showing it to someone:
 * Team SmartBin, with a leader, a member and an adviser, five chapters at
 * different stages with real PDF drafts, open and finished tasks, adviser
 * feedback, comments and six weeks of activity. Dates count back from today,
 * so the deadlines, countdown and weekly stats always look current.
 */
internal class SampleGroup @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val database: ThesisFlowDatabase,
    private val userDao: UserDao,
    private val groupDao: GroupDao,
    private val memberDao: MemberDao,
    private val chapterDao: ChapterDao,
    private val versionDao: ChapterVersionDao,
    private val fileDao: FileDao,
    private val taskDao: TaskDao,
    private val commentDao: TaskCommentDao,
    private val feedbackDao: FeedbackDao,
    private val activity: LocalActivityLog,
    private val fileStore: LocalFileStore,
) {
    /** Adds the group and its three people, and returns the leader's id. */
    suspend fun create(): String = Run().create()

    /** One sample group's ids and dates; a new run makes a separate group. */
    private inner class Run {
        private val zone = ZoneId.systemDefault()
        private val today = LocalDate.now(zone)
        private val now = Instant.now()

        private val groupId = newId()
        private val carla = newId()
        private val dan = newId()
        private val reyes = newId()
        private val people = listOf(
            Triple(carla, "Carla Mendoza", Role.LEADER),
            Triple(dan, "Dan Villanueva", Role.MEMBER),
            Triple(reyes, "Prof. Liza Reyes", Role.ADVISER),
        )

        suspend fun create(): String {
            val chapters = chapters()
            val drafts = withContext(ioDispatcher) {
                drafts(chapters).map { (file, version) ->
                    val stored = fileStore.fileFor(file.id, file.name)
                    writeSamplePdf(stored, chapters.first { it.id == version.chapterId }, version.versionNumber)
                    file.copy(sizeBytes = stored.length()) to version
                }
            }
            try {
                database.withTransaction {
                    addPeopleAndGroup()
                    chapterDao.upsertAll(chapters)
                    fileDao.upsertAll(drafts.map { it.first })
                    versionDao.upsertAll(drafts.map { it.second })
                    drafts.forEach { (file, version) ->
                        record(
                            ActivityTypes.DRAFT_UPLOADED, version.uploadedBy, file.uploadedAt ?: now,
                            chapters.first { it.id == version.chapterId }, detail = version.versionNumber.toString(),
                        )
                    }
                    addFeedback(chapters)
                    addTasks(chapters)
                    record(ActivityTypes.CHAPTER_STATUS, dan, daysAgo(2, 19, 50), chapters[0], ChapterStatus.FOR_REVIEW.toWire())
                    record(ActivityTypes.CHAPTER_STATUS, carla, daysAgo(3, 11, 20), chapters[3], ChapterStatus.DRAFTING.toWire())
                    activity.record(
                        groupId, ActivityTypes.DEFENSE_SCHEDULED, actorId = carla,
                        detail = DefenseKind.PROPOSAL.toWire(), at = now.minus(Duration.ofHours(2)),
                    )
                }
            } catch (e: Exception) {
                fileStore.delete(drafts.map { it.first })
                throw e
            }
            return carla
        }

        private suspend fun addPeopleAndGroup() {
            people.forEach { (uid, name, _) ->
                userDao.upsert(
                    UserEntity(
                        uid = uid,
                        email = "",
                        displayName = name,
                        photoUrl = null,
                        course = COURSE,
                        school = SCHOOL,
                        onboardingComplete = true,
                    ),
                )
            }
            groupDao.upsert(
                GroupEntity(
                    id = groupId,
                    name = "Team SmartBin",
                    thesisTitle = "SmartBin: An IoT-Based Waste Segregation System",
                    course = COURSE,
                    school = SCHOOL,
                    myRole = Role.LEADER,
                    memberCount = people.size,
                    proposalDefenseAt = daysAhead(23, 9, 0),
                    finalDefenseAt = null,
                    createdAt = daysAgo(45, 8, 30),
                    updatedAt = now,
                ),
            )
            val joined = listOf(daysAgo(45, 8, 30), daysAgo(44, 9, 15), daysAgo(43, 15, 40))
            people.zip(joined).forEach { (person, at) ->
                val (uid, name, role) = person
                memberDao.upsert(MemberEntity(groupId, uid, name, photoUrl = null, role = role, joinedAt = at))
                if (role != Role.LEADER) {
                    activity.record(groupId, ActivityTypes.MEMBER_JOINED, actorId = uid, detail = role.toWire(), at = at)
                }
            }
        }

        private fun chapters(): List<ChapterEntity> = listOf(
            Triple("Introduction", ChapterStatus.FOR_REVIEW, 2L),
            Triple("Review of Related Literature", ChapterStatus.REVISIONS, 9L),
            Triple("Methodology", ChapterStatus.NOT_STARTED, 20L),
            Triple("Results and Discussion", ChapterStatus.DRAFTING, 44L),
            Triple("Summary, Conclusions and Recommendations", ChapterStatus.NOT_STARTED, 58L),
        ).mapIndexed { index, (title, status, dueInDays) ->
            newChapterEntity(groupId, title, index + 1, now).copy(
                status = status,
                deadline = daysAhead(dueInDays, 23, 59),
                latestVersion = when (index) {
                    0 -> 2
                    1 -> 4
                    else -> 0
                },
            )
        }

        /** Two drafts of Chapter 1 and four of Chapter 2, each a file and its version. */
        private fun drafts(chapters: List<ChapterEntity>): List<Pair<FileEntity, ChapterVersionEntity>> {
            val introduction = chapters[0]
            val literature = chapters[1]
            return listOf(
                Draft(introduction, 1, dan, daysAgo(5, 14, 20), "Chapter1_Introduction_v1.pdf", "First full draft"),
                Draft(introduction, 2, dan, daysAgo(2, 19, 45), "Chapter1_Introduction_v2.pdf", "Added the local studies in 1.2"),
                Draft(literature, 1, carla, daysAgo(30), "Chapter2_RRL_v1.pdf", "Foreign studies"),
                Draft(literature, 2, dan, daysAgo(16), "Chapter2_RRL_v2.pdf", "Added local studies"),
                Draft(literature, 3, carla, daysAgo(9), "Chapter2_RRL_v3.pdf", "New synthesis"),
                Draft(literature, 4, dan, daysAgo(3), "Chapter2_RRL_v4.pdf", "Sources for Table 2"),
            ).map { draft ->
                val fileId = newId()
                FileEntity(
                    id = fileId,
                    groupId = groupId,
                    name = draft.fileName,
                    mimeType = "application/pdf",
                    // Filled in once the PDF is written.
                    sizeBytes = 0,
                    storagePath = fileStore.storagePath(groupId, fileId, draft.fileName),
                    kind = FileKind.DRAFT,
                    chapterId = draft.chapter.id,
                    taskId = null,
                    feedbackId = null,
                    uploadedBy = draft.by,
                    uploadedAt = draft.at,
                ) to ChapterVersionEntity(
                    groupId = groupId,
                    chapterId = draft.chapter.id,
                    versionNumber = draft.number,
                    fileId = fileId,
                    note = draft.note,
                    uploadedBy = draft.by,
                    uploadedAt = draft.at,
                )
            }
        }

        private suspend fun addFeedback(chapters: List<ChapterEntity>) {
            val introduction = chapters[0]
            val literature = chapters[1]
            // (chapter, version, text, posted, hours until resolved, resolved by)
            listOf(
                FeedbackSeed(literature, 1, "Narrow the scope to Dagupan City.", daysAgo(29, 9, 30), 20, dan),
                FeedbackSeed(literature, 2, "Add three local studies from 2022 onwards.", daysAgo(15, 16, 10), 52, carla),
                FeedbackSeed(literature, 3, "Table 2 needs a source.", daysAgo(8, 8, 50), 6, dan),
                FeedbackSeed(
                    literature, 4, "Rewrite the synthesis paragraph so it leads into the research gap.",
                    daysAgo(2, 13, 5), null, null,
                ),
                FeedbackSeed(
                    introduction, 2,
                    "Good progress. Cite the 2023 DENR waste data in 1.1, and turn the objectives in 1.3 into measurable ones.",
                    now.minus(Duration.ofHours(3)), null, null,
                ),
            ).forEach { seed ->
                val resolvedAt = seed.resolvedAfterHours?.let { seed.posted.plus(Duration.ofHours(it)) }
                feedbackDao.upsertAll(
                    listOf(
                        FeedbackEntity(
                            id = newId(),
                            groupId = groupId,
                            chapterId = seed.chapter.id,
                            body = seed.body,
                            authorId = reyes,
                            authorName = "Prof. Liza Reyes",
                            authorRole = Role.ADVISER,
                            versionNumber = seed.version,
                            resolved = resolvedAt != null,
                            resolvedBy = seed.resolvedBy,
                            resolvedAt = resolvedAt,
                            createdAt = seed.posted,
                            hasPendingWrites = false,
                        ),
                    ),
                )
                record(ActivityTypes.FEEDBACK_POSTED, reyes, seed.posted, seed.chapter, detail = seed.version.toString())
                if (resolvedAt != null && seed.resolvedBy != null) {
                    record(ActivityTypes.FEEDBACK_RESOLVED, seed.resolvedBy, resolvedAt, seed.chapter)
                }
            }
            record(ActivityTypes.CHAPTER_STATUS, reyes, daysAgo(2, 13, 6), literature, ChapterStatus.REVISIONS.toWire())
        }

        private suspend fun addTasks(chapters: List<ChapterEntity>) {
            val (introduction, literature, methodology) = chapters
            // Finished over the last six weeks: (title, chapter, assignees, days ago it was done)
            listOf(
                DoneTask("Collect related studies", literature, listOf(dan), 38),
                DoneTask("Outline Chapter 1", introduction, listOf(carla), 33),
                DoneTask("Write the problem statement", introduction, listOf(carla, dan), 26),
                DoneTask("Gather the survey respondents list", methodology, listOf(dan), 19),
                DoneTask("Format references in APA 7", literature, listOf(dan), 12),
                DoneTask("Draft the conceptual framework", literature, listOf(carla), 10),
                DoneTask("Revise 1.2 after feedback", introduction, listOf(dan), 4),
                DoneTask("Prepare consent forms", methodology, listOf(carla), 1),
            ).forEach { done ->
                val created = daysAgo(done.daysAgo + 7, 9, 0)
                val completed = daysAgo(done.daysAgo, 16, 0)
                val task = task(done.title, "", TaskPriority.MEDIUM, TaskStatus.DONE, daysAgo(done.daysAgo - 1, 23, 59), done.chapter, created)
                    .copy(completedAt = completed, completedBy = done.assignees.first())
                saveTask(task, done.assignees)
                taskRecord(ActivityTypes.TASK_CREATED, carla, created, task)
                taskRecord(ActivityTypes.TASK_STATUS, done.assignees.first(), completed, task, TaskStatus.DONE.toWire())
            }

            val questionnaireCreated = daysAgo(9, 9, 10)
            val questionnaire = task(
                "Draft the survey questionnaire", "For the respondents in 3.4", TaskPriority.HIGH,
                TaskStatus.IN_PROGRESS, null, methodology, questionnaireCreated,
            )
            saveTask(questionnaire, listOf(dan))
            taskRecord(ActivityTypes.TASK_CREATED, carla, questionnaireCreated, questionnaire)
            taskRecord(ActivityTypes.TASK_STATUS, dan, daysAgo(6, 8, 30), questionnaire, TaskStatus.IN_PROGRESS.toWire())
            listOf(
                Triple(dan, daysAgo(6, 10, 0), "First 15 questions are in the shared doc."),
                Triple(carla, daysAgo(5, 10, 0), "Can we add a Likert scale for Q8 to Q12?"),
                Triple(dan, daysAgo(5, 15, 0), "Done. Also split the demographics into their own section."),
                Triple(reyes, daysAgo(4, 10, 0), "Have two respondents outside the group pilot it before Friday."),
            ).forEach { (author, at, body) ->
                commentDao.upsertAll(
                    listOf(
                        TaskCommentEntity(
                            id = newId(),
                            groupId = groupId,
                            taskId = questionnaire.id,
                            body = body,
                            authorId = author,
                            authorName = people.first { it.first == author }.second,
                            createdAt = at,
                            hasPendingWrites = false,
                        ),
                    ),
                )
            }

            // One overdue for the member, one coming up for the leader.
            listOf(
                task("Encode pilot survey results", "", TaskPriority.HIGH, TaskStatus.TODO, daysAgo(2, 23, 59), null, daysAgo(20)) to dan,
                task("Book the defense room", "", TaskPriority.HIGH, TaskStatus.TODO, daysAhead(5, 17, 0), null, daysAgo(20)) to carla,
            ).forEach { (task, assignee) ->
                saveTask(task, listOf(assignee))
                taskRecord(ActivityTypes.TASK_CREATED, carla, task.createdAt ?: now, task)
            }
        }

        private fun task(
            title: String,
            description: String,
            priority: TaskPriority,
            status: TaskStatus,
            dueAt: Instant?,
            chapter: ChapterEntity?,
            createdAt: Instant,
        ) = TaskEntity(
            id = newId(),
            groupId = groupId,
            title = title,
            description = description,
            status = status,
            priority = priority,
            dueAt = dueAt,
            chapterId = chapter?.id,
            createdBy = carla,
            createdAt = createdAt,
            completedAt = null,
            completedBy = null,
            hasPendingWrites = false,
        )

        private suspend fun saveTask(task: TaskEntity, assignees: List<String>) {
            taskDao.upsertAll(listOf(TaskRow(task, assignees.map { TaskAssigneeEntity(task.id, it, groupId) })))
        }

        private suspend fun record(type: String, actor: String, at: Instant, chapter: ChapterEntity, detail: String? = null) =
            activity.record(groupId, type, actorId = actor, targetId = chapter.id, targetTitle = chapter.title, detail = detail, at = at)

        private suspend fun taskRecord(type: String, actor: String, at: Instant, task: TaskEntity, detail: String? = null) =
            activity.record(groupId, type, actorId = actor, targetId = task.id, targetTitle = task.title, detail = detail, at = at)

        private fun daysAgo(days: Long, hour: Int = 10, minute: Int = 0): Instant =
            today.minusDays(days).atTime(hour, minute).atZone(zone).toInstant()

        private fun daysAhead(days: Long, hour: Int, minute: Int): Instant =
            today.plusDays(days).atTime(hour, minute).atZone(zone).toInstant()
    }

    /** A one-page PDF standing in for a draft, so opening a version shows a real document. */
    private fun writeSamplePdf(target: File, chapter: ChapterEntity, version: Int) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            val title = Paint().apply {
                isAntiAlias = true
                textSize = 20f
                isFakeBoldText = true
            }
            val body = Paint().apply {
                isAntiAlias = true
                textSize = 12f
            }
            page.canvas.drawText("Chapter ${chapter.sortOrder}: ${chapter.title}", MARGIN, 96f, title)
            listOf(
                "SmartBin: An IoT-Based Waste Segregation System",
                "Team SmartBin · Draft v$version",
                "",
                "A sample draft made by ThesisFlow's local build.",
            ).forEachIndexed { line, text -> page.canvas.drawText(text, MARGIN, 132f + line * 20f, body) }
            document.finishPage(page)
            target.parentFile?.mkdirs()
            target.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private data class Draft(
        val chapter: ChapterEntity,
        val number: Int,
        val by: String,
        val at: Instant,
        val fileName: String,
        val note: String,
    )

    private data class FeedbackSeed(
        val chapter: ChapterEntity,
        val version: Int,
        val body: String,
        val posted: Instant,
        val resolvedAfterHours: Long?,
        val resolvedBy: String?,
    )

    private data class DoneTask(
        val title: String,
        val chapter: ChapterEntity,
        val assignees: List<String>,
        val daysAgo: Long,
    )

    private companion object {
        const val COURSE = "BS Computer Science"
        const val SCHOOL = "PHINMA University of Pangasinan"
        /** A4 at 72 points per inch. */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 56f
    }
}
