package com.telegramyou.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.auth.AuthFormState
import com.telegramyou.app.ui.auth.AuthScreen
import com.telegramyou.app.ui.chat.ChatScreen
import com.telegramyou.app.ui.chat.ChatUiState
import com.telegramyou.app.ui.home.HomeScreen
import com.telegramyou.app.ui.home.HomeUiState
import com.telegramyou.app.ui.theme.TelegramYouTheme

/**
 * Renderable previews of every screen.
 *
 * These exist to be turned into PNGs: nothing in CI draws a screen, so
 * layout, spacing and colour have been unverified since the project started.
 * The screenshot plugin renders what is in this source set through layoutlib,
 * with no device and no emulator.
 *
 * They are cheap to write only because the screens were rebuilt to take a
 * UiState and callbacks. A screen that held a repository could not be
 * previewed at all without standing up TDLib.
 *
 * Dynamic colour is off here on purpose: layoutlib has no wallpaper to take
 * it from, and a preview that changes with the host would be useless for
 * comparing one commit against the next.
 */

private val sampleChats = listOf(
    ChatPreview(
        id = 1,
        title = "Material Design",
        lastMessage = "Expressive motion is live ✨",
        timestampLabel = "12:41",
        unreadCount = 3,
        isPinned = true,
        isOnline = true
    ),
    ChatPreview(
        id = 2,
        title = "Lina Park",
        lastMessage = "Did you try the loading indicator?",
        timestampLabel = "11:02",
        unreadCount = 1
    ),
    ChatPreview(
        id = 3,
        title = "TelegramYou News",
        lastMessage = "Build notes for the alpha",
        timestampLabel = "Mon",
        isChannel = true,
        isMuted = true,
        unreadCount = 12,
        avatarColor = 44
    ),
    ChatPreview(
        id = 4,
        title = "Design crit",
        lastMessage = "Sasha: let's keep the teal identity",
        timestampLabel = "Sun",
        isGroup = true,
        avatarColor = 17
    )
)

private val sampleStories = listOf(
    StoryItem(id = 1, authorName = "You", isOwn = true, hasUnseen = false),
    StoryItem(id = 2, authorName = "Lina", avatarColor = 8),
    StoryItem(id = 3, authorName = "Sasha", hasUnseen = false, avatarColor = 23)
)

@Preview(name = "Chat list", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun HomeScreenPreview() {
    TelegramYouTheme(dynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                me = TelegramUser(id = 1, firstName = "Artem"),
                chats = sampleChats,
                stories = sampleStories
            ),
            onRefresh = {},
            onOpenChat = {},
            onOpenStory = {}
        )
    }
}

@Preview(name = "Chat list, dark", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun HomeScreenDarkPreview() {
    TelegramYouTheme(darkTheme = true, dynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                me = TelegramUser(id = 1, firstName = "Artem"),
                chats = sampleChats,
                stories = sampleStories
            ),
            onRefresh = {},
            onOpenChat = {},
            onOpenStory = {}
        )
    }
}

private fun message(
    id: Long,
    text: String,
    outgoing: Boolean,
    minutesAgo: Long,
    sender: String? = null
) = ChatMessage(
    id = id,
    chatId = 1,
    text = text,
    isOutgoing = outgoing,
    timeLabel = "12:${40 - minutesAgo}",
    // Fixed instants, so day separators and run grouping render the same way
    // on every machine rather than drifting with the clock.
    date = 1_760_000_000L - minutesAgo * 60,
    senderName = sender,
    senderId = if (outgoing) 1L else 2L,
    isRead = outgoing
)

@Preview(name = "Conversation", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun ChatScreenPreview() {
    TelegramYouTheme(dynamicColor = false) {
        ChatScreen(
            state = ChatUiState(
                detail = ChatDetail(
                    chat = sampleChats[0],
                    messages = listOf(
                        message(1, "Welcome to TelegramYou", false, 40, "Material Design"),
                        message(2, "Stock Material 3 Expressive, all the way down.", false, 39, "Material Design"),
                        message(3, "Looks sharp. Let's keep the teal identity.", true, 12),
                        message(4, "Agreed — and dynamic colour stays the default.", true, 11)
                    ),
                    memberCountLabel = "128K subscribers"
                )
            ),
            onBack = {},
            onDraftChange = {},
            onAttachmentPicked = {},
            onAttachmentCleared = {},
            onReplyTo = {},
            onEdit = {},
            onComposerBannerCancelled = {},
            onSend = {},
            onLoadOlder = {},
            onDeleteRequested = {},
            onDeleteDismissed = {},
            onDeleteConfirmed = { _, _ -> }
        )
    }
}

@Preview(name = "Login, phone", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun AuthScreenPreview() {
    TelegramYouTheme(dynamicColor = false) {
        AuthScreen(
            state = AuthFormState(
                auth = AuthUiState(state = AuthState.WaitPhoneNumber),
                phone = "+1 234 567 8900"
            ),
            onPhoneChange = {},
            onCodeChange = {},
            onPasswordChange = {},
            onSubmitPhone = {},
            onSubmitCode = {},
            onSubmitPassword = {},
            onResendCode = {}
        )
    }
}
