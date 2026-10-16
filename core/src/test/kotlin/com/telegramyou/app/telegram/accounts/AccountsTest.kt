package com.telegramyou.app.telegram.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsTest {

    private fun signedIn(id: Int, user: Long, name: String = "User $user") =
        AccountSlot(id = id, userId = user, name = name, phone = "+1555$user")

    @Test
    fun `a phone starts with the one account it always had`() {
        val accounts = Accounts()
        assertEquals(listOf(AccountSlot(0)), accounts.slots)
        assertEquals(0, accounts.active.id)
        assertEquals("tdlib", databaseDirName(0))
        assertEquals("tdlib-files", filesDirName(0))
    }

    @Test
    fun `adding makes a new slot the one in use, and remembers where it came from`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10))).added()
        assertEquals(listOf(0, 1), accounts.slots.map { it.id })
        assertEquals(1, accounts.activeId)
        assertEquals(0, accounts.returnTo)
        assertTrue(accounts.canCancelAdding)
        assertEquals("tdlib-1", databaseDirName(1))
        assertEquals("tdlib-files-1", filesDirName(1))
    }

    @Test
    fun `no more than three, and none while one is still being signed into`() {
        val one = Accounts(slots = listOf(signedIn(0, 10)))
        val adding = one.added()
        assertFalse("adding while an add is unfinished", adding.canAdd)
        assertEquals(adding, adding.added())

        val three = Accounts(slots = listOf(signedIn(0, 10), signedIn(1, 11), signedIn(2, 12)))
        assertFalse(three.canAdd)
        assertEquals(three, three.added())
    }

    @Test
    fun `giving up an add goes back to the account it started from`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10), signedIn(1, 11)), activeId = 1)
            .added()
        assertEquals(2, accounts.activeId)
        val back = accounts.removed(2)
        assertEquals(listOf(0, 1), back.slots.map { it.id })
        assertEquals(1, back.activeId)
        assertNull(back.returnTo)
    }

    @Test
    fun `signing in completes the add`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10))).added()
            .withProfile(1, userId = 11, name = "Second", phone = "+15550011", photoPath = null)
        assertNull(accounts.returnTo)
        assertFalse(accounts.canCancelAdding)
        assertEquals("Second", accounts.active.name)
        assertTrue(accounts.canAdd)
    }

    @Test
    fun `signing out of the one in use moves to the first left`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10), signedIn(1, 11), signedIn(2, 12)), activeId = 1)
        val after = accounts.removed(1)
        assertEquals(listOf(0, 2), after.slots.map { it.id })
        assertEquals(0, after.activeId)
    }

    @Test
    fun `removing another account leaves the one in use alone`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10), signedIn(1, 11)), activeId = 1)
        assertEquals(1, accounts.removed(0).activeId)
    }

    @Test
    fun `the last account is never removed, only emptied`() {
        val accounts = Accounts(slots = listOf(signedIn(2, 12)), activeId = 2)
        val after = accounts.removed(2)
        assertEquals(listOf(AccountSlot(2)), after.slots)
        assertEquals(2, after.activeId)
    }

    @Test
    fun `the same person signed in twice is found`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10), AccountSlot(1)), activeId = 1)
        assertEquals(0, accounts.duplicateOf(1, 10)?.id)
        assertNull(accounts.duplicateOf(1, 99))
        assertNull(accounts.duplicateOf(0, 10))
    }

    @Test
    fun `the others are the signed-in accounts not in use`() {
        val accounts = Accounts(slots = listOf(signedIn(0, 10), signedIn(1, 11), AccountSlot(2)), activeId = 0)
        assertEquals(listOf(1), accounts.others.map { it.id })
    }

    @Test
    fun `what is written reads back the same`() {
        val accounts = Accounts(
            slots = listOf(
                signedIn(0, 10, name = "Tab\tand\nnewline — ünïcode"),
                AccountSlot(3, userId = 13, name = "", phone = "", photoPath = "/data/x y/photo.jpg")
            ),
            activeId = 3,
            returnTo = 0
        )
        assertEquals(accounts, decodeAccounts(accounts.encode()))
    }

    @Test
    fun `nothing written, or something unreadable, is the one account`() {
        assertEquals(Accounts(), decodeAccounts(null))
        assertEquals(Accounts(), decodeAccounts(""))
        assertEquals(Accounts(), decodeAccounts("garbage"))
        assertEquals(Accounts(), decodeAccounts("v1 0 -1\n"))
    }

    @Test
    fun `an active id that is gone falls back to the first account`() {
        val text = "v1 7 -1\n" + Accounts(slots = listOf(signedIn(0, 10), signedIn(1, 11))).encode().lines().drop(1).joinToString("\n")
        val accounts = decodeAccounts(text)
        assertEquals(0, accounts.activeId)
        assertEquals(2, accounts.slots.size)
    }
}
