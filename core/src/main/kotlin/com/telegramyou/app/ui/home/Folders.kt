package com.telegramyou.app.ui.home

/**
 * Chat folders, as tabs over the one chat list.
 *
 * A folder is a filter, not a place: the same chat can be in several, and
 * every one of them draws the row the main list draws. So nothing here moves
 * chats anywhere — it decides which tabs exist, which chat belongs under
 * which, and what number sits on the tab.
 *
 * Pure, and generic over the chat type, so the tests can use something
 * smaller than a `ChatPreview` and the rules are checked without a device.
 */

/**
 * One tab above the chat list.
 *
 * [id] is null for "All", which is not a folder on the server and has no id
 * there. Modelling it as a tab with no id rather than as a special case in
 * every caller is what keeps the selection one nullable value instead of a
 * flag and a number that can disagree.
 */
data class FolderTab(val id: Int?, val title: String)

/** The title "All" carries, kept here so the screen does not invent its own. */
const val ALL_CHATS_TAB = "All"

/**
 * The tabs to draw, or none at all.
 *
 * An account with no folders gets an empty list rather than a lone "All"
 * tab: a tab strip with one tab in it is a row of chrome that filters
 * nothing, and most accounts have no folders.
 */
fun <T> folderTabs(
    folders: List<T>,
    id: (T) -> Int,
    title: (T) -> String
): List<FolderTab> = if (folders.isEmpty()) {
    emptyList()
} else {
    listOf(FolderTab(id = null, title = ALL_CHATS_TAB)) +
        folders.map { FolderTab(id = id(it), title = title(it)) }
}

/**
 * The chats under [folderId], or all of them when it is null.
 *
 * The archive is excluded by the caller, not here: a folder is a filter over
 * whatever list it is applied to, and the archive screen uses the same
 * filter over a different list.
 */
fun <T> chatsInFolder(
    chats: List<T>,
    folderId: Int?,
    folderIds: (T) -> Set<Int>
): List<T> = if (folderId == null) chats else chats.filter { folderId in folderIds(it) }

/**
 * The selection to use, given what is currently selected and what exists.
 *
 * A folder can be deleted on another device while its tab is the one on
 * screen. Falling back to All is the only answer that leaves a list to look
 * at; keeping the id would show an empty tab that nothing can select away
 * from, because its tab is gone.
 */
fun <T> selectedFolder(folders: List<T>, id: (T) -> Int, requested: Int?): Int? =
    if (requested != null && folders.none { id(it) == requested }) null else requested

/**
 * How many chats in [folderId] have something unread.
 *
 * Chats rather than messages, which is what Telegram's own folder badge
 * counts: the question a tab answers is "is there anything in here for me",
 * and a single chat with two hundred unread messages is one answer to it.
 *
 * Muted chats are counted too. A muted chat is one that should not make a
 * sound, not one that should be invisible — and a folder whose badge
 * disappears because everything in it is muted looks empty when it is not.
 */
fun <T> folderUnreadChats(
    chats: List<T>,
    folderId: Int?,
    folderIds: (T) -> Set<Int>,
    unreadCount: (T) -> Int
): Int = chatsInFolder(chats, folderId, folderIds).count { unreadCount(it) > 0 }
