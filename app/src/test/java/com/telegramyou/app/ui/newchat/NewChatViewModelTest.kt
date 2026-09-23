package com.telegramyou.app.ui.newchat

import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.InviteLinkPreview
import com.telegramyou.app.telegram.model.TelegramUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun installDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun removeDispatcher() = Dispatchers.resetMain()

    private val lina = TelegramUser(id = 11, firstName = "Lina")
    private val artem = TelegramUser(id = 12, firstName = "Artem")
    private val dasha = TelegramUser(id = 21, firstName = "Dasha")

    private fun setUp(): Pair<NewChatViewModel, FakeTelegramClient> {
        val client = FakeTelegramClient().apply { contactList = listOf(lina, artem, dasha) }
        return NewChatViewModel(TelegramRepository(client)) to client
    }

    @Test
    fun `members go to the server in list order, not tap order`() = runTest(dispatcher) {
        val (vm, client) = setUp()
        advanceUntilIdle()

        vm.onTitleChange("Design crit")
        vm.onMemberToggled(dasha.id)
        vm.onMemberToggled(lina.id)
        vm.create(NewChatKind.Group)
        advanceUntilIdle()

        assertEquals("Design crit" to listOf(lina.id, dasha.id), client.createdGroups.single())
        assertEquals(901L, vm.uiState.value.openChatId)
    }

    @Test
    fun `ticking twice takes somebody back out`() = runTest(dispatcher) {
        val (vm, _) = setUp()
        vm.onMemberToggled(lina.id)
        vm.onMemberToggled(lina.id)
        assertTrue(vm.uiState.value.selected.isEmpty())
    }

    @Test
    fun `nothing is created without a name`() = runTest(dispatcher) {
        val (vm, client) = setUp()
        vm.onTitleChange("   ")
        vm.create(NewChatKind.Group)
        advanceUntilIdle()
        assertTrue(client.createdGroups.isEmpty())
        assertNull(vm.uiState.value.openChatId)
    }

    @Test
    fun `a channel takes its description with it`() = runTest(dispatcher) {
        val (vm, client) = setUp()
        vm.onTitleChange("Build notes")
        vm.onDescriptionChange("What shipped this week")
        vm.create(NewChatKind.Channel)
        advanceUntilIdle()
        assertEquals("Build notes" to "What shipped this week", client.createdChannels.single())
    }

    @Test
    fun `something that is not an invite link says so without asking the server`() =
        runTest(dispatcher) {
            val (vm, _) = setUp()
            vm.onLinkChange("https://t.me/telegram")
            advanceUntilIdle()
            val state = vm.uiState.value
            assertNotNull(state.linkProblem)
            assertFalse(state.isCheckingLink)
            assertNull(state.preview)
        }

    @Test
    fun `a good link is shown before anything is joined`() = runTest(dispatcher) {
        val (vm, client) = setUp()
        client.invitePreview = InviteLinkPreview(
            link = "https://t.me/+ExpressiveDesignClub",
            title = "Expressive Design Club",
            memberCount = 1284
        )
        vm.onLinkChange("t.me/joinchat/ExpressiveDesignClub")
        advanceUntilIdle()

        assertEquals("Expressive Design Club", vm.uiState.value.preview?.title)
        assertTrue("nothing joined yet", client.joinedLinks.isEmpty())

        vm.join()
        advanceUntilIdle()
        // The canonical spelling, not the one that was pasted.
        assertEquals(listOf("https://t.me/+ExpressiveDesignClub"), client.joinedLinks)
        assertEquals(990L, vm.uiState.value.openChatId)
    }

    @Test
    fun `a link to a chat already joined opens it instead of joining again`() =
        runTest(dispatcher) {
            val (vm, client) = setUp()
            client.invitePreview = InviteLinkPreview(
                link = "https://t.me/+AlreadyIn42",
                title = "Design Circle",
                memberCount = 42,
                joinedChatId = 3L
            )
            vm.onLinkChange("https://t.me/+AlreadyIn42")
            advanceUntilIdle()
            vm.join()
            advanceUntilIdle()
            assertTrue(client.joinedLinks.isEmpty())
            assertEquals(3L, vm.uiState.value.openChatId)
        }

    @Test
    fun `a link to nowhere says so`() = runTest(dispatcher) {
        val (vm, client) = setUp()
        client.invitePreview = null
        vm.onLinkChange("https://t.me/+Expired1234")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.linkProblem)
        assertNull(vm.uiState.value.preview)
    }
}
