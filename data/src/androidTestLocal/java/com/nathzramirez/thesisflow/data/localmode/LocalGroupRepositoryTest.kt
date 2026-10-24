package com.nathzramirez.thesisflow.data.localmode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nathzramirez.thesisflow.domain.model.ActivityEvent
import com.nathzramirez.thesisflow.domain.model.DefaultChapters
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Groups, invites and joining in the local build, checked against the rules joinGroup enforces on the server. */
@RunWith(AndroidJUnit4::class)
class LocalGroupRepositoryTest {

    private val graph = LocalModeTestGraph()
    private val groups = graph.groups

    @After
    fun tearDown() = graph.close()

    @Test
    fun aNewGroupHasItsLeaderAndTheFiveStandardChapters() = runTest {
        val carla = graph.person("Carla Mendoza")
        val groupId = groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()

        val group = groups.observeGroup(groupId).first()
        assertEquals(Role.LEADER, group?.myRole)
        assertEquals(1, group?.memberCount)
        assertEquals(listOf(carla), groups.observeMembers(groupId).first().map { it.uid })
        assertEquals(DefaultChapters.titles, graph.chapters.observeChapters(groupId).first().map { it.title })
    }

    @Test
    fun eachPersonSeesOnlyTheirOwnGroupsWithTheirOwnRole() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER, "Prof. Liza Reyes" to Role.ADVISER)

        graph.person("Eve Santos")
        assertTrue(groups.observeMyGroups().first().isEmpty())
        assertNull(groups.observeGroup(team.groupId).first())

        graph.switchTo(team.others[1])
        val seenByAdviser = groups.observeMyGroups().first().single()
        assertEquals(Role.ADVISER, seenByAdviser.myRole)
        assertEquals(3, seenByAdviser.memberCount)
    }

    @Test
    fun joiningRecordsTheRoleAndAnActivityEntry() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        val dan = team.others.single()

        assertEquals(Role.MEMBER, groups.observeMembers(team.groupId).first().single { it.uid == dan }.role)
        val joined = graph.feed(team.groupId).single()
        assertEquals(ActivityEvent.MemberJoined(Role.MEMBER), joined.event)
        assertEquals("Dan Villanueva", joined.actorName)
    }

    @Test
    fun aNewCodeRevokesTheOldOneAndCodesAreReadLoosely() = runTest {
        graph.person("Carla Mendoza")
        val groupId = groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        val first = groups.createInvite(groupId, Role.MEMBER).value().code
        val second = groups.createInvite(groupId, Role.MEMBER).value().code
        assertEquals(listOf(second), groups.observeInvites(groupId).first().map { it.code })

        graph.person("Dan Villanueva")
        assertEquals(DomainError.InviteInvalid, groups.joinGroup(first).error())
        assertEquals(DomainError.InviteInvalid, groups.joinGroup("ZZZZZZ").error())
        assertEquals(groupId, groups.joinGroup(" ${second.lowercase().chunked(3).joinToString("-")} ").value())
        // Joining again changes nothing, like the joinGroup function.
        assertEquals(groupId, groups.joinGroup(second).value())
        assertEquals(2, groups.observeMembers(groupId).first().size)
    }

    @Test
    fun anExpiredCodeIsRefused() = runTest {
        graph.person("Carla Mendoza")
        val groupId = groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        graph.session.replaceInvite(LocalInvite("K7QM2P", groupId, Role.MEMBER, Instant.now().minusSeconds(1)))

        graph.person("Dan Villanueva")
        assertEquals(DomainError.InviteExpired, groups.joinGroup("K7QM2P").error())
    }

    @Test
    fun aGroupHoldsTenPeople() = runTest {
        val leader = graph.person("Leader")
        val groupId = groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        val code = groups.createInvite(groupId, Role.MEMBER).value().code
        repeat(9) { n ->
            graph.person("Member $n")
            groups.joinGroup(code).value()
        }

        graph.person("One too many")
        assertEquals(DomainError.GroupFull, groups.joinGroup(code).error())
        graph.switchTo(leader)
        assertEquals(10, groups.observeGroup(groupId).first()?.memberCount)
    }

    @Test
    fun onlyTheLeaderMakesCodes() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        graph.switchTo(team.others.single())

        assertEquals(DomainError.PermissionDenied, groups.createInvite(team.groupId, Role.MEMBER).error())
    }

    @Test
    fun theLastPersonOutTakesTheGroupWithThem() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)

        graph.switchTo(team.others.single())
        groups.leaveGroup(team.groupId).value()
        assertNotNull(graph.db.localModeDao().group(team.groupId))
        assertTrue(graph.feed(team.groupId).any { it.event == ActivityEvent.MemberLeft && it.actorName == "Dan Villanueva" })

        graph.switchTo(team.leader)
        groups.leaveGroup(team.groupId).value()
        assertNull(graph.db.localModeDao().group(team.groupId))
        assertTrue(graph.chapters.observeChapters(team.groupId).first().isEmpty())
        assertTrue(graph.feed(team.groupId).isEmpty())
    }

    @Test
    fun settingADefenseDateIsNewsButClearingItIsNot() = runTest {
        graph.person("Carla Mendoza")
        val groupId = groups.createGroup("Team SmartBin", "SmartBin", "BSCS", "UPang").value()
        val proposal = Instant.now().plus(23, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)

        groups.setDefenseDate(groupId, DefenseKind.PROPOSAL, proposal).value()
        groups.setDefenseDate(groupId, DefenseKind.PROPOSAL, null).value()

        assertNull(groups.observeGroup(groupId).first()?.proposalDefenseAt)
        assertEquals(listOf(ActivityEvent.DefenseScheduled(DefenseKind.PROPOSAL)), graph.feed(groupId).map { it.event })
    }
}
