package com.telegramyou.app.telegram.model

/**
 * Every track in every chat, laid out the way a music app lays out a
 * library — the owner's experiment for 1.6.4, off by default under For
 * geeks. Built from nothing but what Telegram gives a message: a title, a
 * performer, a cover thumbnail, the chat it is in, who sent it and when,
 * and whether it was posted as one of several together.
 *
 * Telegram has no albums, so an album here is what a person would call one:
 * tracks posted together as a single post. An artist is a performer. A
 * playlist is a chat with music in it — Saved Messages first, then the rest
 * by how much there is.
 */
data class MusicLibrary(
    /** Every track once, A to Z. */
    val tracks: List<LibraryTrack> = emptyList(),
    val artists: List<LibraryCollection> = emptyList(),
    val albums: List<LibraryCollection> = emptyList(),
    val playlists: List<LibraryCollection> = emptyList(),
    /** The newest tracks from anywhere: what has just arrived. */
    val latest: List<LibraryTrack> = emptyList(),
    /**
     * The newest tracks sent by people — in private chats and groups, not
     * channels. "What your chats are playing", honestly named: Telegram does
     * not say who listened to what, only who sent it.
     */
    val fromPeople: List<LibraryTrack> = emptyList(),
    /**
     * Saved Messages' music, newest first, even a single track: where a
     * person keeps what they mean to keep, so the front page leads with it —
     * the owner's call for 1.6.6. Null when Saved Messages has none.
     */
    val saved: LibraryCollection? = null
) {
    val isEmpty: Boolean get() = tracks.isEmpty()

    fun collection(key: String): LibraryCollection? =
        (albums.asSequence() + artists + playlists + listOfNotNull(saved)).firstOrNull { it.key == key }
}

/** A track with where it came from, for the social half of the library. */
data class LibraryTrack(
    val message: ChatMessage,
    val track: Track,
    val chatTitle: String,
    val fromChannel: Boolean
) {
    /**
     * "Sent by Lina in Design Circle", or just the chat's name for a
     * channel's post and for one's own — "Sent by You in Saved Messages"
     * said the same thing twice under the Saved Messages heading.
     */
    val provenance: String
        get() = when {
            fromChannel || message.isOutgoing || track.senderName.isBlank() -> chatTitle
            track.senderName == chatTitle -> "Sent by ${track.senderName}"
            else -> "Sent by ${track.senderName} in $chatTitle"
        }
}

enum class CollectionKind { Album, Artist, Playlist }

data class LibraryCollection(
    /** Stable across rebuilds, for navigating to it: "album:42", "artist:shape shifters", "chat:13". */
    val key: String,
    val kind: CollectionKind,
    val title: String,
    val subtitle: String,
    /** In play order: an album as posted, an artist and a playlist newest first. */
    val tracks: List<LibraryTrack>
) {
    /** The first track that has a cover, for the tile and the page's header. */
    val cover: Track? get() = tracks.firstOrNull { it.track.coverPath != null || it.track.coverFileId != null }?.track
}

/** What the library needs to know of a chat. */
data class LibraryChat(val title: String, val isChannel: Boolean = false, val isSaved: Boolean = false)

/**
 * The library from [messages] — every music message found, in any order —
 * and what is known of their [chats].
 */
fun buildLibrary(messages: List<ChatMessage>, chats: Map<Long, LibraryChat>): MusicLibrary {
    val all = messages
        .distinctBy { it.chatId to it.id }
        .mapNotNull { message ->
            val track = message.asTrack() ?: return@mapNotNull null
            val chat = chats[message.chatId]
            LibraryTrack(message, track, chat?.title.orEmpty(), chat?.isChannel == true)
        }
        .sortedByDescending { it.message.date }

    // The same song forwarded into Saved Messages is one song in the lists
    // of songs, and still one of each chat's in its playlist.
    val unique = all.distinctBy { Triple(it.track.title.lowercase(), it.track.performer.lowercase(), it.track.durationSeconds) }

    val artists = unique
        .filter { it.track.performer.isNotBlank() }
        .groupBy { it.track.performer.trim().lowercase() }
        .map { (key, tracks) ->
            val name = tracks.first().track.performer.trim()
            LibraryCollection(
                key = "artist:$key",
                kind = CollectionKind.Artist,
                title = name,
                subtitle = countLabel(tracks.size),
                tracks = tracks
            )
        }
        .sortedWith(compareByDescending<LibraryCollection> { it.tracks.size }.thenBy { it.title.lowercase() })

    val albums = all
        .filter { it.track.albumId != null }
        .groupBy { it.message.chatId to it.track.albumId }
        .filterValues { it.size >= 2 }
        .map { (key, tracks) ->
            // As it was posted: first to last.
            val ordered = tracks.sortedBy { it.message.id }
            val performers = ordered.map { it.track.performer.trim() }.filter { it.isNotBlank() }.distinct()
            val chatTitle = ordered.first().chatTitle
            LibraryCollection(
                key = "album:${key.first}:${key.second}",
                kind = CollectionKind.Album,
                title = performers.singleOrNull() ?: chatTitle.ifBlank { "Album" },
                subtitle = listOf("Album", countLabel(ordered.size), chatTitle.takeIf { performers.size == 1 && it.isNotBlank() })
                    .filterNotNull().joinToString(" · "),
                tracks = ordered
            )
        }
        .sortedByDescending { album -> album.tracks.maxOf { it.message.date } }

    // Two tracks make a chat a playlist — except Saved Messages, which is
    // one from its first: it leads the front page, and a playlist tab
    // without it looked as if it had been lost.
    val playlists = all
        .groupBy { it.message.chatId }
        .filter { (chatId, tracks) -> tracks.size >= 2 || chats[chatId]?.isSaved == true }
        .map { (chatId, tracks) ->
            val chat = chats[chatId]
            LibraryCollection(
                key = "chat:$chatId",
                kind = CollectionKind.Playlist,
                title = chat?.title?.ifBlank { null } ?: "Chat",
                subtitle = countLabel(tracks.size),
                tracks = tracks
            )
        }
        .sortedWith(
            compareByDescending<LibraryCollection> { chats[it.key.removePrefix("chat:").toLongOrNull()]?.isSaved == true }
                .thenByDescending { it.tracks.size }
                .thenBy { it.title.lowercase() }
        )

    val saved = all
        .filter { chats[it.message.chatId]?.isSaved == true }
        .takeIf { it.isNotEmpty() }
        ?.let { tracks ->
            val chatId = tracks.first().message.chatId
            LibraryCollection(
                key = "chat:$chatId",
                kind = CollectionKind.Playlist,
                title = chats[chatId]?.title?.ifBlank { null } ?: "Saved Messages",
                subtitle = countLabel(tracks.size),
                tracks = tracks
            )
        }

    return MusicLibrary(
        saved = saved,
        tracks = unique.sortedBy { it.track.title.lowercase() },
        artists = artists,
        albums = albums,
        playlists = playlists,
        latest = unique.take(LATEST),
        fromPeople = unique.filter { !it.fromChannel && !it.message.isOutgoing }.take(LATEST)
    )
}

private fun countLabel(count: Int) = if (count == 1) "1 track" else "$count tracks"

private const val LATEST = 10
