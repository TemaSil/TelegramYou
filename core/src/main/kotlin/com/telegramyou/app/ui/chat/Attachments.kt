package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.AttachmentDraft

/**
 * What can be done to photos and files waiting above the composer (2.0):
 * more added, one taken out, one moved. Kept here, on the JVM, because each
 * has an edge — the album's ten, the last one out leaving nothing, a move
 * past either end — that is easy to get wrong in a composable.
 */

/** Telegram's album: at most ten photos or files go as one. */
const val ALBUM_LIMIT = 10

/**
 * [added] joined onto what is waiting, when the two are the same kind —
 * photos onto photos, files onto files, the ones already there skipped —
 * and up to [ALBUM_LIMIT]. Anything else replaces it: a recording is not
 * sent alongside photos, and photos are not sent in an album of files.
 */
fun AttachmentDraft?.plus(added: AttachmentDraft): AttachmentDraft = when {
    this is AttachmentDraft.Photos && added is AttachmentDraft.Photos -> {
        val merged = (uris + added.uris.filter { it !in uris }).take(ALBUM_LIMIT)
        AttachmentDraft.Photos(merged, (spoilered + added.spoilered).filterTo(mutableSetOf()) { it in merged })
    }
    this is AttachmentDraft.Files && added is AttachmentDraft.Files -> {
        val pairs = (uris.zip(names) + added.uris.zip(added.names).filter { (uri, _) -> uri !in uris })
            .take(ALBUM_LIMIT)
        AttachmentDraft.Files(pairs.map { it.first }, pairs.map { it.second })
    }
    added is AttachmentDraft.Photos -> added.copy(uris = added.uris.distinct().take(ALBUM_LIMIT))
    else -> added
}

/** A photo in or out: what a tap on one in the recent strip does. */
fun AttachmentDraft?.toggled(photo: String): AttachmentDraft? {
    val photos = (this as? AttachmentDraft.Photos)?.uris ?: return plus(AttachmentDraft.Photos(listOf(photo)))
    return if (photo in photos) without(photos.indexOf(photo)) else plus(AttachmentDraft.Photos(listOf(photo)))
}

/** The photos or files waiting, one at a time, as the tray shows them. */
val AttachmentDraft.count: Int
    get() = when (this) {
        is AttachmentDraft.Photos -> uris.size
        is AttachmentDraft.Files -> uris.size
        else -> 1
    }

/** All but the one at [index]; null once nothing is left. */
fun AttachmentDraft.without(index: Int): AttachmentDraft? = when (this) {
    is AttachmentDraft.Photos -> uris.filterIndexed { i, _ -> i != index }.takeIf { it.isNotEmpty() }
        ?.let { kept -> AttachmentDraft.Photos(kept, spoilered.filterTo(mutableSetOf()) { it in kept }) }
    is AttachmentDraft.Files -> {
        val kept = uris.indices.filter { it != index }
        if (kept.isEmpty()) null else AttachmentDraft.Files(kept.map { uris[it] }, kept.map { names[it] })
    }
    else -> if (index == 0) null else this
}

/** The one at [from] put at [to], the rest closing up; past either end, the end. */
fun AttachmentDraft.moved(from: Int, to: Int): AttachmentDraft {
    fun <T> List<T>.move(): List<T> {
        if (from !in indices) return this
        val target = to.coerceIn(0, lastIndex)
        if (target == from) return this
        return toMutableList().apply { add(target, removeAt(from)) }
    }
    return when (this) {
        is AttachmentDraft.Photos -> copy(uris = uris.move())
        is AttachmentDraft.Files -> AttachmentDraft.Files(uris.move(), names.move())
        else -> this
    }
}

/** [photo] under a spoiler, or out from under it (2.2). */
fun AttachmentDraft.Photos.withSpoiler(photo: String, on: Boolean): AttachmentDraft.Photos =
    if (photo !in uris) this else copy(spoilered = if (on) spoilered + photo else spoilered - photo)

/**
 * [old] swapped for [new] where it stands — a photo edited before it goes
 * (2.2) — keeping its place and its spoiler.
 */
fun AttachmentDraft.Photos.replaced(old: String, new: String): AttachmentDraft.Photos {
    val at = uris.indexOf(old)
    if (at < 0 || new in uris) return this
    return AttachmentDraft.Photos(
        uris = uris.toMutableList().apply { set(at, new) },
        spoilered = if (old in spoilered) spoilered - old + new else spoilered
    )
}

/**
 * The same pictures as files rather than photos — "Send as files", which
 * Telegram does not compress (2.2). Named by their place, since a picked
 * photo's uri says nothing a person would call it.
 */
fun AttachmentDraft.Photos.asFiles(): AttachmentDraft.Files =
    AttachmentDraft.Files(uris, uris.indices.map { "photo-${it + 1}.jpg" })
