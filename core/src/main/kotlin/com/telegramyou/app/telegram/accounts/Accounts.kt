package com.telegramyou.app.telegram.accounts

import java.util.Base64

/**
 * One account on this phone: a TDLib database of its own, and what the
 * switcher shows of it while that database is not open.
 *
 * Slot 0 is the account the app had before there could be several, and
 * keeps the directories it always had; see [databaseDirName].
 */
data class AccountSlot(
    val id: Int,
    /** Telegram's id for the person signed in here; 0 until someone is. */
    val userId: Long = 0,
    val name: String = "",
    val phone: String = "",
    /** Their picture on this phone, once downloaded. */
    val photoPath: String? = null
) {
    val isSignedIn: Boolean get() = userId != 0L
}

/**
 * The accounts on this phone (2.3), the one in use, and — while one is being
 * added — the one to go back to if that is given up.
 *
 * The official app holds three; so does this one ([MAX_ACCOUNTS]).
 */
data class Accounts(
    val slots: List<AccountSlot> = listOf(AccountSlot(0)),
    val activeId: Int = 0,
    /** Set while an account is being added: where Cancel goes back to. */
    val returnTo: Int? = null
) {
    val active: AccountSlot get() = slots.firstOrNull { it.id == activeId } ?: slots.first()

    /** The others, signed in, in the order they were added. */
    val others: List<AccountSlot> get() = slots.filter { it.id != activeId && it.isSignedIn }

    val canAdd: Boolean get() = slots.size < MAX_ACCOUNTS && slots.all { it.isSignedIn }

    /** Whether the account being signed into can be given up for another. */
    val canCancelAdding: Boolean get() = returnTo != null && slots.any { it.id == returnTo }

    /** A new, empty account, made the one in use; Cancel returns to this one. */
    fun added(): Accounts {
        if (!canAdd) return this
        val id = (slots.maxOf { it.id }) + 1
        return copy(slots = slots + AccountSlot(id), activeId = id, returnTo = activeId)
    }

    fun switchedTo(id: Int): Accounts =
        if (slots.none { it.id == id }) this else copy(activeId = id, returnTo = null)

    /**
     * Without [id] — signed out of, or given up while being added. The one
     * in use moves to the account being returned to, or the first left.
     * The last one is never removed: an empty slot takes its place, so
     * there is always somewhere to sign in.
     */
    fun removed(id: Int): Accounts {
        val left = slots.filter { it.id != id }
        if (left.isEmpty()) {
            return Accounts(slots = listOf(AccountSlot(id)), activeId = id)
        }
        val next = when {
            id != activeId -> activeId
            returnTo != null && left.any { it.id == returnTo } -> returnTo
            else -> left.first().id
        }
        return copy(slots = left, activeId = next, returnTo = if (next == activeId) returnTo else null)
    }

    /**
     * Who is signed in to [id], as TDLib said once it was ready. Signing in
     * completes an add, so there is nothing to go back to after it.
     */
    fun withProfile(id: Int, userId: Long, name: String, phone: String, photoPath: String?): Accounts =
        copy(
            slots = slots.map {
                if (it.id == id) it.copy(userId = userId, name = name, phone = phone, photoPath = photoPath ?: it.photoPath) else it
            },
            returnTo = if (id == activeId && userId != 0L) null else returnTo
        )

    /** Another slot already signed in as [userId], other than [id]: the same person twice. */
    fun duplicateOf(id: Int, userId: Long): AccountSlot? =
        slots.firstOrNull { it.id != id && it.userId == userId && userId != 0L }

    /** One line per account, then the one in use; see [decodeAccounts]. */
    fun encode(): String = buildString {
        append("v1 ").append(activeId).append(' ').append(returnTo ?: -1).append('\n')
        slots.forEach { slot ->
            append(slot.id).append('\t')
                .append(slot.userId).append('\t')
                .append(b64(slot.name)).append('\t')
                .append(b64(slot.phone)).append('\t')
                .append(b64(slot.photoPath.orEmpty())).append('\n')
        }
    }
}

/** The most accounts one phone holds, as in the official app. */
const val MAX_ACCOUNTS = 3

/**
 * The accounts as [Accounts.encode] wrote them; the single account the app
 * always had when there is nothing written yet, or what is written cannot
 * be read.
 */
fun decodeAccounts(text: String?): Accounts {
    if (text.isNullOrBlank()) return Accounts()
    return runCatching {
        // Not trimmed: a line ends in tabs when its last fields are empty,
        // and trimming them away lost the line.
        val lines = text.lines().filter { it.isNotEmpty() }
        val head = lines.first().split(' ')
        require(head[0] == "v1")
        val slots = lines.drop(1).map { line ->
            val f = line.split('\t')
            AccountSlot(
                id = f[0].toInt(),
                userId = f[1].toLong(),
                name = unb64(f.getOrElse(2) { "" }),
                phone = unb64(f.getOrElse(3) { "" }),
                photoPath = unb64(f.getOrElse(4) { "" }).ifEmpty { null }
            )
        }.distinctBy { it.id }
        require(slots.isNotEmpty())
        val active = head[1].toInt().takeIf { id -> slots.any { it.id == id } } ?: slots.first().id
        val returnTo = head[2].toInt().takeIf { id -> id >= 0 && slots.any { it.id == id } }
        Accounts(slots = slots, activeId = active, returnTo = returnTo)
    }.getOrElse { Accounts() }
}

/**
 * Where an account's TDLib database lives, under the app's files. Slot 0
 * keeps the directory the app always used, so updating to a version with
 * accounts keeps the one already signed in.
 */
fun databaseDirName(id: Int): String = if (id == 0) "tdlib" else "tdlib-$id"

/** Where an account's downloaded files live; see [databaseDirName]. */
fun filesDirName(id: Int): String = if (id == 0) "tdlib-files" else "tdlib-files-$id"

private fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())
private fun unb64(text: String): String = String(Base64.getDecoder().decode(text))
