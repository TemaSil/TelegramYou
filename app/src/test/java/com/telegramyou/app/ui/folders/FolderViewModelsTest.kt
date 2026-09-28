package com.telegramyou.app.ui.folders

import androidx.lifecycle.SavedStateHandle
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.FolderRules
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
class FolderViewModelsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun installDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun removeDispatcher() = Dispatchers.resetMain()

    private fun editor(client: FakeTelegramClient, folderId: Int) =
        FolderEditViewModel(
            TelegramRepository(client),
            SavedStateHandle(mapOf(Route.FolderEdit.ARG_FOLDER_ID to folderId))
        )

    @Test
    fun `a new folder saves only once it is named and lets something in`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        val vm = editor(client, Route.FolderEdit.NEW)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isNew)

        vm.onNameChange("Work")
        vm.onSave()
        advanceUntilIdle()
        assertTrue("nothing in it yet, so nothing sent", client.savedFolders.isEmpty())

        vm.onRulesChange { it.copy(includeGroups = true) }
        vm.onRulesChange { it.copy(excludeMuted = true) }
        vm.onSave()
        advanceUntilIdle()
        val (id, rules) = client.savedFolders.single()
        assertNull(id)
        assertEquals("Work", rules.name)
        assertTrue("two quick changes both kept", rules.includeGroups && rules.excludeMuted)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `the name is held to Telegram's length as it is typed`() = runTest(dispatcher) {
        val vm = editor(FakeTelegramClient(), Route.FolderEdit.NEW)
        vm.onNameChange("A very long folder name")
        assertEquals(FolderRules.MAX_NAME, vm.uiState.value.rules.name.length)
    }

    @Test
    fun `an existing folder loads, changes and deletes`() = runTest(dispatcher) {
        val client = FakeTelegramClient().apply {
            setFolders(listOf(ChatFolder(4, "News")))
            folderRulesById[4] = FolderRules(name = "News", includeChannels = true)
        }
        val vm = editor(client, 4)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isNew)
        assertEquals("News", vm.uiState.value.rules.name)

        vm.onChatToggled(42)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(4, client.savedFolders.single().first)
        assertEquals(listOf(42L), client.savedFolders.single().second.includedChatIds)

        vm.onDeleteRequested()
        vm.onDeleteConfirmed()
        advanceUntilIdle()
        assertEquals(listOf(4), client.deletedFolders)
    }

    @Test
    fun `folders move one place at a time and not past the ends`() = runTest(dispatcher) {
        val client = FakeTelegramClient().apply {
            setFolders(listOf(ChatFolder(1, "Work"), ChatFolder(2, "People"), ChatFolder(3, "News")))
        }
        val vm = FoldersViewModel(TelegramRepository(client))
        vm.onMove(3, -1)
        advanceUntilIdle()
        assertEquals(listOf(1, 3, 2), client.folderOrder)

        client.folderOrder = null
        vm.onMove(1, -1)
        advanceUntilIdle()
        assertNull("the first cannot move up", client.folderOrder)
    }
}
