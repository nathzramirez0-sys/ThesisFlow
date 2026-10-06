package com.nathzramirez.thesisflow.domain.usecase.reminder

import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Reminder
import com.nathzramirez.thesisflow.domain.model.ReminderPlanner
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.ReminderLog
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The reminders due now that haven't been shown yet. Reads only the local cache,
 * so reminders work offline. The caller marks them sent once they are on screen,
 * so a reminder lost to a crash is shown on the next run instead of never.
 */
class CollectDueRemindersUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val chapterRepository: ChapterRepository,
    private val taskRepository: TaskRepository,
    private val reminderLog: ReminderLog,
) {
    suspend operator fun invoke(now: Instant, zone: ZoneId): List<Reminder> {
        val uid = (authRepository.authState.first() as? AuthState.SignedIn)?.uid ?: return emptyList()
        val groups = groupRepository.observeMyGroups().first()
        val chapters = chapterRepository.observeAllChapters().first()
        val tasks = groups.flatMap { taskRepository.observeTasks(it.id).first() }

        val planned = ReminderPlanner.plan(now, zone, uid, groups, chapters, tasks)
        if (planned.isEmpty()) return emptyList()
        val sent = reminderLog.sentKeys(planned.map { it.key })
        return planned.filter { it.key !in sent }
    }
}
