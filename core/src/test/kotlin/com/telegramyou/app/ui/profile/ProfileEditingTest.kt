package com.telegramyou.app.ui.profile

import com.telegramyou.app.telegram.model.TelegramUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditingTest {

    private val me = TelegramUser(
        id = 1,
        firstName = "You",
        lastName = "Expressive",
        username = "telegramyou",
        bio = "Material You, in a Telegram client"
    )

    private fun fieldsOf(draft: ProfileDraft) = validateProfile(draft).map { it.field }

    @Test
    fun `a draft taken from an account has nothing to save`() {
        assertFalse(canSaveProfile(me, profileDraftOf(me)))
        assertEquals(emptySet<ProfileField>(), profileChanges(me, profileDraftOf(me)))
    }

    @Test
    fun `an account without a username fills the field with empty, not null`() {
        val nameless = me.copy(username = null)
        assertEquals("", profileDraftOf(nameless).username)
        // And a draft of it is unchanged, which would not hold if null and ""
        // compared unequal here.
        assertFalse(canSaveProfile(nameless, profileDraftOf(nameless)))
    }

    @Test
    fun `whitespace either side of a value is not an edit`() {
        val draft = profileDraftOf(me).copy(firstName = "  You  ")
        assertEquals(emptySet<ProfileField>(), profileChanges(me, draft))
        assertFalse(canSaveProfile(me, draft))
    }

    @Test
    fun `only the fields that differ are reported`() {
        val draft = profileDraftOf(me).copy(firstName = "Someone", bio = "New bio")
        assertEquals(setOf(ProfileField.FirstName, ProfileField.Bio), profileChanges(me, draft))
        assertTrue(canSaveProfile(me, draft))
    }

    @Test
    fun `a first name is required`() {
        val draft = profileDraftOf(me).copy(firstName = "   ")
        assertEquals(listOf(ProfileField.FirstName), fieldsOf(draft))
        assertFalse(canSaveProfile(me, draft))
    }

    @Test
    fun `a last name may be empty`() {
        val draft = profileDraftOf(me).copy(lastName = "")
        assertEquals(emptyList<ProfileField>(), fieldsOf(draft))
        assertTrue(canSaveProfile(me, draft))
    }

    @Test
    fun `names are capped at sixty-four`() {
        val draft = profileDraftOf(me).copy(
            firstName = "a".repeat(MAX_NAME_LENGTH + 1),
            lastName = "b".repeat(MAX_NAME_LENGTH + 1)
        )
        assertEquals(
            listOf(ProfileField.FirstName, ProfileField.LastName),
            fieldsOf(draft)
        )
    }

    @Test
    fun `a name of exactly sixty-four is allowed`() {
        val draft = profileDraftOf(me).copy(firstName = "a".repeat(MAX_NAME_LENGTH))
        assertEquals(emptyList<ProfileField>(), fieldsOf(draft))
    }

    @Test
    fun `a bio is capped at seventy`() {
        val draft = profileDraftOf(me).copy(bio = "x".repeat(MAX_BIO_LENGTH + 1))
        assertEquals(listOf(ProfileField.Bio), fieldsOf(draft))
    }

    @Test
    fun `clearing a username is allowed`() {
        // Giving the username up is a real thing to want, so an empty field is
        // not an error even though a five-character minimum applies to any
        // username that is actually there.
        val draft = profileDraftOf(me).copy(username = "")
        assertEquals(emptyList<ProfileField>(), fieldsOf(draft))
        assertTrue(canSaveProfile(me, draft))
    }

    @Test
    fun `a username shorter than five is refused`() {
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "abcd"))
        )
    }

    @Test
    fun `a username longer than thirty-two is refused`() {
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "a".repeat(MAX_USERNAME_LENGTH + 1)))
        )
    }

    @Test
    fun `a username must start with a letter`() {
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "1abcde"))
        )
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "_abcde"))
        )
    }

    @Test
    fun `a username may not end with an underscore`() {
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "abcde_"))
        )
    }

    @Test
    fun `a username may not hold two underscores in a row`() {
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "ab__cde"))
        )
    }

    @Test
    fun `a single underscore inside a username is fine`() {
        assertEquals(
            emptyList<ProfileField>(),
            fieldsOf(profileDraftOf(me).copy(username = "ab_cde"))
        )
    }

    @Test
    fun `digits are allowed after the first character`() {
        assertEquals(
            emptyList<ProfileField>(),
            fieldsOf(profileDraftOf(me).copy(username = "user42"))
        )
    }

    @Test
    fun `a Cyrillic username is refused`() {
        // The point of spelling out ASCII rather than using Char.isLetter,
        // which is Unicode-wide and would have accepted this.
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "привет"))
        )
    }

    @Test
    fun `an Arabic-Indic digit is refused`() {
        // Char.isDigit accepts these too.
        assertEquals(
            listOf(ProfileField.Username),
            fieldsOf(profileDraftOf(me).copy(username = "user٤٥"))
        )
    }

    @Test
    fun `every broken field is reported at once`() {
        val draft = ProfileDraft(
            firstName = "",
            lastName = "b".repeat(MAX_NAME_LENGTH + 1),
            bio = "x".repeat(MAX_BIO_LENGTH + 1),
            username = "no"
        )
        assertEquals(
            listOf(
                ProfileField.FirstName,
                ProfileField.LastName,
                ProfileField.Bio,
                ProfileField.Username
            ),
            fieldsOf(draft)
        )
    }

    @Test
    fun `a changed but invalid draft cannot be saved`() {
        val draft = profileDraftOf(me).copy(username = "no")
        assertTrue(profileChanges(me, draft).isNotEmpty())
        assertFalse(canSaveProfile(me, draft))
    }

    @Test
    fun `every problem carries a message worth showing`() {
        val problems = validateProfile(ProfileDraft(firstName = "", username = "no"))
        assertTrue(problems.isNotEmpty())
        assertTrue(problems.all { it.message.isNotBlank() })
    }
}
