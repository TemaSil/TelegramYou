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
    /** TDLib's `prefer_ipv6`: reach Telegram over IPv6 where both are offered. */
    val preferIpv6: Boolean = false
)

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
