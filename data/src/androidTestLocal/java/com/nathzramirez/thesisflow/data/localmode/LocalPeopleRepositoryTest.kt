package com.nathzramirez.thesisflow.data.localmode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.model.Person
import com.nathzramirez.thesisflow.domain.model.Role
import com.nathzramirez.thesisflow.domain.model.TaskStatus
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** The people list that stands in for accounts, and the sample group. */
@RunWith(AndroidJUnit4::class)
class LocalPeopleRepositoryTest {

    private val graph = LocalModeTestGraph()
    private val people = graph.people

    @After
    fun tearDown() = graph.close()

    @Test
    fun aNewPersonIsSignedInUntilTheySwitchAwayWithoutFinishing() = runTest {
        people.addPerson().value()
        val uid = graph.session.requireUid()
        assertEquals(AuthState.SignedIn(uid), graph.auth.authState.first())
        assertFalse(graph.users.observeCurrentUser().first()!!.onboardingComplete)

        graph.auth.signOut()

        assertEquals(AuthState.SignedOut, graph.auth.authState.first())
        assertTrue(people.observePeople().first().isEmpty())
    }

    @Test
    fun someoneSetUpStaysOnTheList() = runTest {
        val carla = graph.person("Carla Mendoza")
        graph.auth.signOut()

        assertEquals(listOf(Person(carla, "Carla Mendoza", emptyList())), people.observePeople().first())
    }

    @Test
    fun theListShowsEachPersonsGroupsAndRoles() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)

        val dan = people.observePeople().first().single { it.uid == team.others.single() }
        assertEquals(listOf(Person.Membership("Team SmartBin", Role.MEMBER)), dan.memberships)
    }

    @Test
    fun renamingSomeoneRenamesThemInEveryGroup() = runTest {
        val team = graph.groupWith("Carla Mendoza", "Dan Villanueva" to Role.MEMBER)
        graph.switchTo(team.others.single())

        graph.users.updateProfile("Daniel Villanueva", "BS Computer Science", "UPang").value()

        val names = graph.groups.observeMembers(team.groupId).first().map { it.displayName }
        assertTrue("Daniel Villanueva" in names)
        assertFalse("Dan Villanueva" in names)
    }

    @Test
    fun switchingToSomeoneWhoIsNotHereFails() = runTest {
        assertEquals(DomainError.NotFound, people.switchTo("nobody").error())
    }

    @Test
    fun accountsBelongToTheCloudBuild() = runTest {
        assertTrue(graph.auth.signInWithEmail("carla@test.com", "thesis2026").error() is DomainError.Unknown)
    }

    @Test
    fun theSampleGroupHasThreePeopleAndAHistory() = runTest {
        people.addSampleGroup().value()

        assertEquals(
            listOf("Carla Mendoza", "Dan Villanueva", "Prof. Liza Reyes"),
            people.observePeople().first().map { it.displayName },
        )
        assertEquals("Carla Mendoza", graph.users.observeCurrentUser().first()?.displayName)
        val group = graph.groups.observeMyGroups().first().single()
        assertEquals(Role.LEADER, group.myRole)
        assertEquals(3, group.memberCount)
        assertTrue(group.proposalDefenseAt!!.isAfter(Instant.now()))

        val chapters = graph.chapters.observeChapters(group.id).first()
        assertEquals(5, chapters.size)
        val drafts = graph.chapters.observeGroupVersions(group.id).first()
        assertEquals(6, drafts.size)
        drafts.forEach { version ->
            val file = version.file!!
            val stored = graph.fileStore.fileFor(file.id, file.name)
            assertTrue(stored.exists())
            assertEquals(stored.length(), file.sizeBytes)
        }

        val tasks = graph.tasks.observeTasks(group.id).first()
        assertEquals(11, tasks.size)
        assertEquals(8, tasks.count { it.status == TaskStatus.DONE })
        assertEquals(2, graph.feedback.observeOpenCounts(group.id).first().values.sum())
        assertTrue(graph.feed(group.id).size > 20)
    }
}
