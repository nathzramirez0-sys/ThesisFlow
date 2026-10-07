package com.nathzramirez.thesisflow.domain.usecase.stats

import com.nathzramirez.thesisflow.domain.model.GroupStats
import com.nathzramirez.thesisflow.domain.model.GroupStatsCalculator
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.FeedbackRepository
import com.nathzramirez.thesisflow.domain.repository.GroupRepository
import com.nathzramirez.thesisflow.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The group's dashboard, recomputed whenever any of its inputs change in the
 * local cache, so it updates live as teammates finish work.
 */
class ObserveGroupStatsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val chapterRepository: ChapterRepository,
    private val taskRepository: TaskRepository,
    private val feedbackRepository: FeedbackRepository,
) {
    operator fun invoke(groupId: String, zone: ZoneId, clock: () -> Instant = Instant::now): Flow<GroupStats> {
        val chapterSide = combine(
            groupRepository.observeMembers(groupId),
            chapterRepository.observeChapters(groupId),
            chapterRepository.observeGroupVersions(groupId),
            ::Triple,
        )
        val taskSide = combine(
            taskRepository.observeTasks(groupId),
            feedbackRepository.observeGroupFeedback(groupId),
            taskRepository.observeGroupComments(groupId),
            ::Triple,
        )
        return combine(chapterSide, taskSide) { (members, chapters, versions), (tasks, feedback, comments) ->
            GroupStatsCalculator.compute(clock(), zone, members, chapters, versions, tasks, feedback, comments)
        }
    }
}
