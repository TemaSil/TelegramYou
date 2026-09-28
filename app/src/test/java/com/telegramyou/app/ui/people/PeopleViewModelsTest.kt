package com.telegramyou.app.ui.people

import androidx.lifecycle.SavedStateHandle
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.PersonProfile
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun installDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun removeDispatcher() = Dispatchers.resetMain()

    private val lina = TelegramUser(id = 11, firstName = "Lina", lastName = "Park", bio = "Swims at dawn")

    private fun client() = FakeTelegramClient().apply {
        people[lina.id] = PersonProfile(lina, isContact = true, isBlocked = false)
        contactList = listOf(lina)
    }

    @Test
    fun `a profile blocks, unblocks, and opens a chat only when asked`() = runTest(dispatcher) {
        val client = client()
        val vm = PersonViewModel(
            TelegramRepository(client),
            SavedStateHandle(mapOf(Route.Person.ARG_USER_ID to lina.id))
        )
        advanceUntilIdle()
        assertEquals("Swims at dawn", vm.uiState.value.profile?.user?.bio)
        assertFalse("opening a profile makes no chat", client.chatCalls.any { it.startsWith("openPrivateChat") })

        vm.onBlockedChange(true)
        advanceUntilIdle()
        assertTrue(lina.id in client.blocked)
        assertTrue(vm.uiState.value.profile!!.isBlocked)

        vm.onBlockedChange(false)
        advanceUntilIdle()
        assertFalse(lina.id in client.blocked)

        vm.onSendMessage()
        advanceUntilIdle()
        assertEquals(lina.id, vm.uiState.value.openChatId)
        vm.onChatOpened()
        assertNull(vm.uiState.value.openChatId)
    }

    @Test
    fun `unblocking from the list takes them off it and says so`() = runTest(dispatcher) {
        val client = client().apply { blocked += lina.id }
        val vm = BlockedViewModel(TelegramRepository(client))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf(lina), vm.uiState.value.people)

        vm.onUnblock(lina)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.people.isEmpty())
        assertEquals("Lina is unblocked", vm.uiState.value.message)
        assertTrue(client.blocked.isEmpty())
    }

    @Test
    fun `a number not on Telegram keeps the dialog up and says so`() = runTest(dispatcher) {
        val client = client().apply { addContactResult = null }
        val vm = ContactsViewModel(TelegramRepository(client))
        advanceUntilIdle()

        vm.onAddRequested()
        vm.onDraftChange(ContactDraft(phone = "+15550100404", firstName = "Nobody"))
        vm.onAddConfirmed()
        advanceUntilIdle()

        assertEquals(listOf("+15550100404"), client.addedContacts)
        assertEquals("+15550100404 is not on Telegram", vm.uiState.value.message)
        assertEquals("Nobody", vm.uiState.value.draft?.firstName)
        assertNull(vm.uiState.value.openChatId)
    }

    @Test
    fun `an added contact goes straight to the chat with them`() = runTest(dispatcher) {
        val client = client().apply { addContactResult = 77L }
        val vm = ContactsViewModel(TelegramRepository(client))
        advanceUntilIdle()

        vm.onAddRequested()
        vm.onDraftChange(ContactDraft(phone = "+15550100077", firstName = "Pavel"))
        vm.onAddConfirmed()
        advanceUntilIdle()

        assertNull("the dialog closes", vm.uiState.value.draft)
        assertEquals(77L, vm.uiState.value.openChatId)
    }

    @Test
    fun `the dialog will not send a number without a name`() = runTest(dispatcher) {
        assertFalse(ContactDraft(phone = "+15550100077").isComplete)
        assertFalse(ContactDraft(phone = "+1555", firstName = "Pavel").isComplete)
        assertTrue(ContactDraft(phone = "+15550100077", firstName = "Pavel").isComplete)
    }
}
