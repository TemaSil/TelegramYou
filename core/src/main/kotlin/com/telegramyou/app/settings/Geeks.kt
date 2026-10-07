package com.telegramyou.app.settings

/**
 * Settings → For geeks: the small things Nekogram and its kind taught
 * people to want, which a stock client does not do. Everything is off until
 * somebody turns it on, so the client behaves as it always did for anyone
 * who never opens the screen.
 */
data class GeekSettings(
    val doubleTap: DoubleTapAction = DoubleTapAction.Nothing,
    /** Message times as 14:03:27 rather than 14:03. */
    val showSeconds: Boolean = false,
    /** "Details" in a message's menu: its exact time, its id and its sender's. */
    val messageDetails: Boolean = false,
    /** "Save to Downloads" and "Copy photo" in a message's menu. */
    val saveMedia: Boolean = false,
    /** Forwards arrive as the forwarder's own messages, with no "Forwarded from". */
    val forwardWithoutQuote: Boolean = false,
    val hideStories: Boolean = false,
    /** The All tab off the chat list when there are folders to page between. */
    val hideAllChatsTab: Boolean = false,
    /**
     * Search opens with the field waiting and the keyboard down. By default
     * the field takes focus and the keyboard comes up, since opening search
     * is almost always the first half of typing into it.
     */
    val searchWithoutKeyboard: Boolean = false,
    /** TDLib's `prefer_ipv6`: reach Telegram over IPv6 where both are offered. */
    val preferIpv6: Boolean = false,
    /** The music library, an experiment: every chat's music as albums, artists and playlists. */
    val musicLibrary: Boolean = false,
    /**
     * More in a message's menu (1.8, after Nekogram): Repeat it here, Save
     * it to Saved Messages, Delete its downloaded file from the phone.
     */
    val messageExtras: Boolean = false,
    /** The archive opened by pulling the chat list down past its top. */
    val openArchiveOnPull: Boolean = false,
    /** Messages from people one has blocked left out of groups. */
    val hideBlockedInGroups: Boolean = false,
    /** A voice or video message asked about before it goes, held or locked. */
    val confirmRecordings: Boolean = false,
    /** No sound or vibration for messages from people not in contacts. */
    val silenceNonContacts: Boolean = false,
    /**
     * Translate chats (2.0): a chat's info offers Translate messages, which
     * shows what comes into it in the phone's language. Off, no chat is
     * translated, whatever each was set to.
     */
    val autoTranslate: Boolean = false,
    /**
     * The composer in one capsule, field and buttons together — how it was
     * until 2.0, when the owner found it heavy and it became the field and
     * a round button on the chat's own background, as Google Messages has it.
     */
    val composerCapsule: Boolean = false,
    /**
     * A group's or channel's header shows its members' faces, overlapping,
     * each in its own shape — the header every group had from 1.x to 2.0.
     * Off, it shows the chat's own picture, as Telegram does (2.0.1, on the
     * owner's word); kept here as a beta, to be worked on.
     */
    val groupFaces: Boolean = false
)

/** What [GeekSettings.messageExtras] puts in a message's menu. */
enum class MessageExtra { Repeat, SaveToSavedMessages, DeleteFile }

/** What a double tap on a message does. */
enum class DoubleTapAction(val label: String) {
    Nothing("Nothing"),
    React("React with ❤️"),
    Reply("Reply"),
    Copy("Copy the text")
}

/** The quick reaction a double tap puts on a message — Telegram's own default. */
const val QUICK_REACTION = "❤️"

/**
 * How many tabs to drop from the front of the folder strip: the All tab, when
 * it is asked to go and there is somewhere else to be. With no other folder,
 * All is the whole list and stays.
 */
fun hiddenLeadingTabs(tabIds: List<Int?>, hideAll: Boolean): Int =
    if (hideAll && tabIds.size > 1 && tabIds.first() == null) 1 else 0
