package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryTest {

    private fun song(
        id: Long,
        chatId: Long,
        title: String,
        performer: String,
        date: Long,
        album: Long? = null,
        sender: String? = null,
        outgoing: Boolean = false
    ) = ChatMessage(
        id = id, chatId = chatId, text = "", isOutgoing = outgoing, timeLabel = "", date = date,
        senderName = sender, contentType = MessageContentType.Audio, albumId = album,
        audio = AudioContent(title = title, performer = performer, durationSeconds = 200, fileId = id.toInt())
    )

    private val chats = mapOf(
        1L to LibraryChat("Material Sound", isChannel = true),
        2L to LibraryChat("Lina Park"),
        6L to LibraryChat("Saved Messages", isSaved = true)
    )

    private val messages = listOf(
        song(10, 1, "Tonal Spot", "Material Sound", date = 100),
        song(11, 1, "Wavy Line", "Shape Shifters", date = 200, album = 7),
        song(12, 1, "Surface Tint", "Shape Shifters", date = 200, album = 7),
        song(13, 1, "Cookie Nine", "Shape Shifters", date = 200, album = 7),
        song(20, 2, "Spring Back", "Material Sound", date = 300, sender = "Lina Park"),
        // The same song, forwarded into Saved Messages.
        song(30, 6, "Tonal Spot", "Material Sound", date = 400, outgoing = true),
        song(31, 6, "Morning Light", "Tonal Collective", date = 410, outgoing = true)
    )

    private val library = buildLibrary(messages, chats)

    @Test
    fun `a song forwarded twice is one song, but in both playlists`() {
        assertEquals(6, library.tracks.size)
        assertEquals(listOf("Cookie Nine", "Morning Light", "Spring Back", "Surface Tint", "Tonal Spot", "Wavy Line"), library.tracks.map { it.track.title })
        assertEquals(2, library.playlists.first { it.title == "Saved Messages" }.tracks.size)
    }

    @Test
    fun `an album is tracks posted together, in the order posted`() {
        val album = library.albums.single()
        assertEquals("Shape Shifters", album.title)
        assertEquals(listOf("Wavy Line", "Surface Tint", "Cookie Nine"), album.tracks.map { it.track.title })
        assertTrue(album.subtitle.startsWith("Album · 3 tracks"))
    }

    @Test
    fun `artists by how much of them there is`() {
        assertEquals(listOf("Shape Shifters", "Material Sound", "Tonal Collective"), library.artists.map { it.title })
        assertEquals("2 tracks", library.artists[1].subtitle)
    }

    @Test
    fun `saved messages leads the playlists`() {
        assertEquals(listOf("Saved Messages", "Material Sound"), library.playlists.map { it.title })
    }

    @Test
    fun `what people sent is not what channels posted, nor what I saved`() {
        assertEquals(listOf("Spring Back"), library.fromPeople.map { it.track.title })
        assertEquals("Sent by Lina Park", library.fromPeople.single().provenance)
        assertEquals("Material Sound", library.albums.single().tracks.first().provenance)
    }

    @Test
    fun `saved messages is its own shelf, newest first`() {
        val saved = library.saved!!
        assertEquals(listOf("Morning Light", "Tonal Spot"), saved.tracks.map { it.track.title })
        assertEquals("Saved Messages", saved.tracks.first().provenance)
        assertEquals("Saved Messages", library.collection(saved.key)?.title)
    }

    @Test
    fun `one saved track is still a shelf, and none is no shelf`() {
        val one = buildLibrary(messages.filter { it.id != 31L }, chats)
        assertEquals(listOf("Tonal Spot"), one.saved?.tracks?.map { it.track.title })
        assertEquals("1 track", one.saved?.subtitle)
        assertEquals("Saved Messages", one.playlists.first().title)
        assertEquals(null, buildLibrary(messages.filter { it.chatId != 6L }, chats).saved)
    }

    @Test
    fun `a collection is found again by its key`() {
        val key = library.albums.single().key
        assertEquals("Shape Shifters", library.collection(key)?.title)
    }
}
