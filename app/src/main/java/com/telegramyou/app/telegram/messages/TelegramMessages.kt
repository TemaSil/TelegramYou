package com.telegramyou.app.telegram.messages

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.CallbackAnswer
import com.telegramyou.app.telegram.model.ReplyKeyboard
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.PostSearch
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.ui.media.FileTransfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What can be done to a message once a conversation is open. */
interface TelegramMessages {
    /**
     * Messages as they arrive, from every chat.
     *
     * A `Flow` rather than a `StateFlow`: an arrival is an event, and a
     * state holder would let a subscriber that joins late re-handle the last
     * one. For a notification that means buzzing twice for a message already
     * shown, which is precisely the failure this is meant to avoid.
     *
     * Every subscriber sees every message, including the conversation on
     * screen and the service that posts notifications. Deciding which of
     * them to ignore is `decideNotification` in :core, not this.
     */
    val incomingMessages: Flow<ChatMessage>

    /**
     * Every change to a message the backend hears of — new ones, including
     * our own; sends confirmed under their real id; edits, deletions and
     * reactions from anyone; the other side reading.
     *
     * The open conversation applies these to what it already holds rather
     * than fetching itself again. Like [incomingMessages] this is a stream of
     * events, not state: a subscriber that joins late has the window it just
     * opened, and nothing before that is owed to it.
     */
    val messageUpdates: Flow<MessageUpdate>

    /**
     * [replyToId] answers an existing message, or null for a fresh one.
     * [sendAt], in epoch seconds, schedules it instead of sending it now.
     */
    suspend fun sendText(chatId: Long, text: String, replyToId: Long? = null, sendAt: Long? = null)

    /** Sends a poll; [PollDraft.canSend] has already said it may go. */
    suspend fun sendPoll(chatId: Long, draft: PollDraft)

    /**
     * The messages waiting to go out in this chat, soonest first — a list
     * of their own, never part of the conversation until they are sent.
     */
    suspend fun scheduledMessages(chatId: Long): List<ChatMessage>

    /** Pins a message in its chat, or unpins it. */
    suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean)

    /** Sends a scheduled message now rather than at its time. */
    suspend fun sendScheduledNow(chatId: Long, messageId: Long)

    suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String = "",
        replyToId: Long? = null
    )

    /**
     * Removes a message. [forEveryone] withdraws it for the other side too,
     * which Telegram only permits within a window and only where the
     * message's own `canBeDeletedForEveryone` says so.
     */
    suspend fun deleteMessage(chatId: Long, messageId: Long, forEveryone: Boolean)

    suspend fun editMessage(chatId: Long, messageId: Long, text: String)

    /**
     * The page of messages immediately older than [beforeMessageId], in
     * chronological order like every other list here.
     *
     * An empty result means the conversation has no more history, and the
     * caller should stop asking. That is the only signal there is — there is
     * no total to compare against — so a caller that ignores it will spin.
     */
    suspend fun loadOlderMessages(
        chatId: Long,
        beforeMessageId: Long,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * A page of history with [messageId] in the middle of it, chronological
     * and including the message itself — where a search hit or a pinned
     * message lives when it is older than anything loaded.
     */
    suspend fun loadMessagesAround(
        chatId: Long,
        messageId: Long,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * The page immediately newer than [afterMessageId], chronological. Empty
     * means [afterMessageId] is the newest message there is.
     */
    suspend fun loadNewerMessages(
        chatId: Long,
        afterMessageId: Long,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * Sends copies of [messageIds] from [fromChatId] into [toChatId].
     *
     * A list rather than one id, because Telegram forwards a run as one block:
     * sent one at a time they arrive as separate forwards, each with its own
     * header, which is not what was selected.
     */
    suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        /** As copies, with no "Forwarded from" — Settings → For geeks. */
        withoutQuote: Boolean = false
    )

    /**
     * Fetches a file to this device and answers with its path.
     *
     * Null when the download fails or the backend has nothing to fetch. The
     * caller is a play button, and the only thing it can do about a file that
     * will not arrive is stay where it is.
     */
    suspend fun downloadFile(fileId: Int): String?

    /**
     * Files currently moving, by file id — downloads and uploads alike.
     *
     * A map rather than a flow per file: several can be in flight at once,
     * every bubble wants the one that belongs to it, and a screen that
     * collected one flow per message would hold as many subscriptions as
     * there are photos on it.
     *
     * Entries appear when a transfer starts and are dropped when it ends, so
     * an empty map is the normal state and a bubble asking for a file id
     * that is not in it has nothing to draw.
     */
    val fileTransfers: StateFlow<Map<Int, FileTransfer>>

    /**
     * Adds or withdraws our reaction on a message.
     *
     * One call for both directions, because Telegram has no separate
     * "unreact": choosing what is already chosen removes it. The caller is
     * expected to have updated its own copy already — see `toggleReaction` in
     * :core — since this returns nothing and the round trip is long enough to
     * see.
     */
    suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String)

    /**
     * The emoji this chat permits, in the order to offer them.
     *
     * Not a constant: a group can be restricted to a handful of reactions, or
     * to none at all, and offering one the server will refuse is a tap that
     * fails for a reason the UI could have known.
     */
    suspend fun availableReactions(chatId: Long): List<String>

    /**
     * Messages matching [query] inside one conversation, newest first.
     *
     * Separate from [searchMessages] rather than a chat id on it: Telegram
     * serves the two from different calls, and a global search narrowed
     * afterwards would page through every chat to fill one.
     */
    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * Messages matching [query] across every conversation, newest first.
     *
     * Blank returns nothing, for the same reason [TelegramChats.searchChats]
     * does: an empty field is not a request for the whole history.
     */
    suspend fun searchMessages(query: String, limit: Int = 30): List<MessageHit>

    /**
     * Posts in public channels matching [query], whether or not this account
     * follows them. Telegram allows a few of these a day for free and asks
     * Stars for more; this never pays, and says so in the result instead.
     */
    suspend fun searchPublicPosts(query: String, limit: Int = 30): PostSearch

    /**
     * The photos and videos in a chat, newest first.
     *
     * A search with an empty query and a content filter rather than a listing
     * of its own, because that is what TDLib offers — and it is the same call
     * the conversation's own search makes, which is why an empty query is
     * allowed here and refused there.
     */
    suspend fun chatMedia(chatId: Long, limit: Int = 60): List<ChatMessage>

    /**
     * Votes in a poll, by the options' positions. An empty list takes our
     * vote back, which is how TDLib spells retracting.
     *
     * Returns nothing: the new counts arrive as [MessageUpdate.PollChanged],
     * and the screen has already drawn its own guess at them.
     */
    suspend fun votePoll(chatId: Long, messageId: Long, optionIds: List<Int>)

    /**
     * Presses a bot's callback button and waits for the bot's answer.
     *
     * [data] is the button's payload exactly as it came, base64. A bot that
     * does not answer in time is an error from the server, not a null — the
     * null is a bot that answered with nothing to say.
     */
    suspend fun pressButton(chatId: Long, messageId: Long, data: String): CallbackAnswer?

    /**
     * The bot keyboard each chat currently shows under its composer, by chat.
     *
     * Chat state rather than message state: a bot sets one and it stays until
     * it sends another or removes it, however many messages come between.
     * Most chats have none, so the map is usually empty.
     */
    val replyKeyboards: StateFlow<Map<Long, ReplyKeyboard>>
}
