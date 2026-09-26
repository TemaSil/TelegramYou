package com.telegramyou.app.update

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One update in the app's own "What's new", newest first in [CHANGELOG].
 *
 * Written the way the owner asked for it: upbeat and casual, a person
 * talking rather than a settings screen. English for now, like the rest of
 * the interface; translations come with the languages.
 *
 * Keyed by date rather than by version: the version is the CI run number,
 * which nobody knows until the build exists, and one day's merges ship as
 * whichever builds CI makes of them.
 */
data class ChangelogEntry(
    val date: LocalDate,
    val title: String,
    val items: List<String>
) {
    /** "26 September". */
    val dateLabel: String get() = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
}

/**
 * Every update, newest first. **Add an entry with every merge to main** that
 * a person would notice — CLAUDE.md says the same.
 */
val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        LocalDate.of(2026, 9, 26),
        "Polls, bots and search on steroids",
        listOf(
            "Polls are here 🗳️ One tap to vote, and the results grow right inside the bubble. Quizzes too — with the right answer and why",
            "Make your own: paperclip → Poll in any group or channel. Question, answers, quiz mode, the works",
            "Bots got buttons 🤖 Tap one under a message and the bot answers. Bots with their own keyboard get it right above the text field",
            "Schedule messages ⏰ Hold Send → Schedule message, pick a day and a time. Everything waiting lives under the clock in the chat's header",
            "Music plays right in the chat 🎧 Title, artist, length — no downloading into the void",
            "Search is its own place now: the people you talk to most, recent chats and searches, channels for you — plus tabs and search across public posts all over Telegram 🔎",
            "Tap Search and the keyboard is already up, ready to type (not your vibe? turn it off in For geeks)",
            "Settings look like bare Android now: tidy groups, icons in little circles, everything where you'd look for it ✨",
            "Updates got a screen of their own — this one, with the changelog you're reading 👋",
            "Messages got **bold**, __italic__, ~~strikethrough~~ and ||spoilers|| — just type the markers, they vanish on send ✍️ Links, @mentions and #hashtags are tappable now",
            "Forwards say where they came from, photos sent together land as one neat grid, and you can pin a message right from its menu 📌",
            "Your profile got a glow-up 💫 Big photo, Set photo / Edit / Settings right under your name, and a QR code to share yourself in one tap",
            "Channels are finally channels, not \"groups\" — filters and labels stopped mixing them up"
        )
    ),
    ChangelogEntry(
        LocalDate.of(2026, 9, 25),
        "For geeks, and a pile of small wins",
        listOf(
            "Say hi to For geeks 🧪 Double-tap actions, seconds in timestamps, message details, save and copy photos, forward without the \"Forwarded from\", hide stories — all off until you flip them on",
            "Privacy, devices and storage all live in Settings now. Kick out a session you don't know, clear the cache by type 🧹",
            "Text size is a slider, just like Android's own",
            "GIFs loop on their own, round video messages play right in the chat 🔁",
            "Chats open smoothly, growing out of their own row — no more jank",
            "Fixed \"This set is empty\" on stickers. Your packs are back 💅"
        )
    ),
    ChangelogEntry(
        LocalDate.of(2026, 9, 24),
        "A real Telegram in the APK",
        listOf(
            "The APK is the live client now — sign in to your own account, no keys of your own needed 🔑",
            "A new sign-in screen: your country's flag, the number formats itself, the code sends on the last digit",
            "Sign in with a QR code from another phone, or by email — grown-up stuff",
            "Everyone gets their own avatar shape, and it dances while they type ✍️",
            "Stories open properly and stay open"
        )
    ),
    ChangelogEntry(
        LocalDate.of(2026, 9, 23),
        "Videos, swipeable folders, your own groups",
        listOf(
            "Videos in bubbles: poster, length, tap and go 🎬",
            "Swipe the chat list sideways to flip between folders",
            "See a file loading — the bar sits right on the message",
            "Make your own group or channel, or join one with an invite link",
            "The header with stories slides away as you scroll down and back as you scroll up"
        )
    ),
    ChangelogEntry(
        LocalDate.of(2026, 9, 19),
        "Folders and photos",
        listOf(
            "Folders as tabs above your chats, with unread counts 📂",
            "Full-screen photos: pinch, zoom, swipe down to close",
            "Every photo in a chat as a grid, and your latest shots right in the attach menu",
            "Tap a chat's header for its info",
            "On a tablet the navigation moves to the side"
        )
    ),
    ChangelogEntry(
        LocalDate.of(2026, 9, 16),
        "Material 3 Expressive in full",
        listOf(
            "Colours come from your wallpaper — that's the You in the name 🎨",
            "Voice messages with a waveform: hold to record, let go to send 🎤",
            "Reactions, swipe to reply, select several messages at once",
            "Search in a chat that jumps even to really old messages",
            "Notifications, and replying straight from the shade"
        )
    ),
    ChangelogEntry(
        LocalDate.of(2026, 7, 20),
        "Where it all started",
        listOf(
            "The first version: Telegram on TDLib and Jetpack Compose. And we're off 🚀"
        )
    )
)
