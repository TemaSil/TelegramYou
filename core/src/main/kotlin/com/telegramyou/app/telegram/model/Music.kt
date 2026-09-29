package com.telegramyou.app.telegram.model

import kotlin.random.Random

/** A track in the player: a music message, and what the player shows of it. */
data class Track(
    val messageId: Long,
    val chatId: Long,
    val title: String,
    val performer: String = "",
    val durationSeconds: Int = 0,
    /** TDLib's id for the file, which a download is asked for by. */
    val fileId: Int? = null,
    /** The file on this device, once it is here. */
    val path: String? = null,
    /** The album cover, once it is here. */
    val coverPath: String? = null,
    /** The cover's file id, for fetching it. */
    val coverFileId: Int? = null,
    /** Who sent it into the chat. */
    val senderName: String = "",
    /** Telegram's album, when the track was posted as one of several together. */
    val albumId: Long? = null
)

/** A music message as a track; null for anything else. */
fun ChatMessage.asTrack(): Track? {
    val audio = audio ?: return null
    return Track(
        messageId = id,
        chatId = chatId,
        title = audio.displayTitle,
        performer = audio.performer,
        durationSeconds = audio.durationSeconds,
        fileId = audio.fileId ?: voiceFileId,
        path = audio.path ?: voicePath,
        coverPath = audio.coverPath,
        coverFileId = audio.coverFileId,
        senderName = if (isOutgoing) "You" else senderName.orEmpty(),
        albumId = albumId
    )
}

/** Which way the queue is played. */
enum class QueueOrder(val label: String) {
    /** Down the list, as the chat shows it: newest first. */
    Listed("In order"),
    /** Up the list: oldest first. */
    Reversed("Reversed"),
    Shuffled("Shuffle")
}

enum class RepeatMode(val label: String) {
    Off("Repeat off"),
    All("Repeat all"),
    One("Repeat one")
}

/**
 * The player's queue: every track of one chat, in the chat's list order,
 * newest first — the whole history, paged in as it is reached.
 *
 * The brother's complaint about the official player is the rule here:
 * [tracks] is only ever appended to. Starting a track, changing the order,
 * loading the next page — none of it rebuilds the list, so the list on
 * screen stays where the thumb left it. Only a new chat's music replaces
 * the queue, and that is a new queue.
 */
data class MusicQueue(
    val chatId: Long = 0,
    /** Where it came from, for the player's subtitle: "From Compose Forum". */
    val sourceTitle: String = "",
    val tracks: List<Track> = emptyList(),
    /** The playing track, as an index into [tracks]; -1 for none. */
    val current: Int = -1,
    val order: QueueOrder = QueueOrder.Listed,
    val repeat: RepeatMode = RepeatMode.Off,
    /** The shuffled play order, as indices into [tracks]; empty unless shuffled. */
    val shuffled: List<Int> = emptyList(),
    /** The chat's first track is loaded: there is nothing older to ask for. */
    val isComplete: Boolean = false
) {
    val playing: Track? get() = tracks.getOrNull(current)

    fun indexOf(messageId: Long): Int = tracks.indexOfFirst { it.messageId == messageId }

    /** [page] appended, anything already here left out; the order kept. */
    fun withMore(page: List<Track>, complete: Boolean, random: Random = Random.Default): MusicQueue {
        val known = tracks.mapTo(HashSet()) { it.messageId }
        val fresh = page.filter { it.messageId !in known }
        val added = tracks.size until tracks.size + fresh.size
        return copy(
            tracks = tracks + fresh,
            // New tracks go after everything already in the shuffle, among
            // themselves shuffled, so what was coming up does not change.
            shuffled = if (order == QueueOrder.Shuffled) shuffled + added.shuffled(random) else shuffled,
            isComplete = complete
        )
    }

    fun startingAt(index: Int): MusicQueue = copy(current = index.coerceIn(-1, tracks.lastIndex))

    /** The order changed; the playing track stays, and plays on from where it is. */
    fun ordered(newOrder: QueueOrder, random: Random = Random.Default): MusicQueue = copy(
        order = newOrder,
        shuffled = if (newOrder == QueueOrder.Shuffled) {
            // The playing track first, the rest after it in a random order.
            listOfNotNull(current.takeIf { it >= 0 }) + tracks.indices.filter { it != current }.shuffled(random)
        } else {
            emptyList()
        }
    )

    /**
     * What plays after the current track: on its own at the end of one
     * ([auto]), or because Next was pressed. Null at the end of the queue
     * with repeat off — or where the next track has not been loaded yet,
     * which [needsMore] says.
     */
    fun following(auto: Boolean): Int? {
        if (current < 0 || tracks.isEmpty()) return null
        if (auto && repeat == RepeatMode.One) return current
        val next = step(+1)
        if (next != null) return next
        return if (repeat != RepeatMode.Off && !needsMore()) first() else null
    }

    /** What Previous plays: the track before, wrapping only with repeat on. */
    fun preceding(): Int? {
        if (current < 0 || tracks.isEmpty()) return null
        val before = step(-1)
        if (before != null) return before
        return if (repeat != RepeatMode.Off && isComplete) last() else null
    }

    /**
     * Whether the track after this one is beyond what is loaded: playing
     * down the list, near its end, with older tracks still on the server.
     */
    fun needsMore(within: Int = 0): Boolean =
        !isComplete && order == QueueOrder.Listed && current >= tracks.lastIndex - within

    /**
     * Down the list, post by post — but an album, tracks posted together,
     * plays as it was posted, first to last. The list is newest first, so
     * straight down it an album would arrive at its last track and play
     * backwards: Telegram's own "wrong order across playlists".
     */
    private fun listedOrder(): List<Int> {
        val order = ArrayList<Int>(tracks.size)
        var i = 0
        while (i < tracks.size) {
            val album = tracks[i].albumId
            var end = i
            if (album != null) {
                while (end + 1 < tracks.size && tracks[end + 1].albumId == album) end++
            }
            for (j in end downTo i) order += j
            i = end + 1
        }
        return order
    }

    private fun step(by: Int): Int? = when (order) {
        QueueOrder.Listed -> {
            val listed = listedOrder()
            listed.getOrNull(listed.indexOf(current) + by)
        }
        // Up the list is oldest first, which is already every album's order.
        QueueOrder.Reversed -> (current - by).takeIf { it in tracks.indices }
        QueueOrder.Shuffled -> {
            val at = shuffled.indexOf(current)
            shuffled.getOrNull(at + by)
        }
    }

    private fun first(): Int? = when (order) {
        QueueOrder.Listed -> listedOrder().firstOrNull()
        QueueOrder.Reversed -> tracks.lastIndex
        QueueOrder.Shuffled -> shuffled.firstOrNull()
    }?.takeIf { it in tracks.indices }

    private fun last(): Int? = when (order) {
        QueueOrder.Listed -> listedOrder().lastOrNull()
        QueueOrder.Reversed -> 0
        QueueOrder.Shuffled -> shuffled.lastOrNull()
    }?.takeIf { it in tracks.indices }
}

/**
 * Playback speeds the player offers for long audio, 0.5× to 2×, in the
 * order a tap steps through: faster first, then the slow ones on the way
 * back round to 1×.
 */
val MUSIC_SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)

/** The speed after [speed] in [MUSIC_SPEEDS], back to 1× after the last. */
fun nextMusicSpeed(speed: Float): Float {
    val at = MUSIC_SPEEDS.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
    return MUSIC_SPEEDS[(at + 1).mod(MUSIC_SPEEDS.size)]
}

/**
 * Whether a track should carry on where it was left rather than start over:
 * only a long one — a podcast, a lecture, a mix — and only from somewhere
 * worth coming back to, not its first seconds or its last.
 */
fun resumeFrom(durationSeconds: Int, savedMs: Long): Long? {
    if (durationSeconds < RESUME_MIN_SECONDS) return null
    val end = durationSeconds * 1000L
    return savedMs.takeIf { it in RESUME_SKIP_MS..(end - RESUME_SKIP_MS) }
}

/** Tracks shorter than this start over; see resumeFrom. */
const val RESUME_MIN_SECONDS = 10 * 60
private const val RESUME_SKIP_MS = 10_000L

/** When the sleep timer stops the music. */
enum class SleepTimer(val label: String, val minutes: Int) {
    Off("Off", 0),
    Quarter("15 minutes", 15),
    Half("30 minutes", 30),
    Hour("1 hour", 60),
    EndOfTrack("End of track", 0)
}
