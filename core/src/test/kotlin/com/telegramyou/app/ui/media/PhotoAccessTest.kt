package com.telegramyou.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAccessTest {

    @Test
    fun `below Android 13 asks for storage`() {
        assertEquals(listOf(PERMISSION_READ_EXTERNAL_STORAGE), photoPermissions(26))
        assertEquals(listOf(PERMISSION_READ_EXTERNAL_STORAGE), photoPermissions(32))
    }

    @Test
    fun `Android 13 asks for images only`() {
        assertEquals(listOf(PERMISSION_READ_MEDIA_IMAGES), photoPermissions(33))
    }

    @Test
    fun `Android 14 asks for the partial grant as well`() {
        assertEquals(
            listOf(PERMISSION_READ_MEDIA_IMAGES, PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED),
            photoPermissions(34)
        )
        assertEquals(2, photoPermissions(36).size)
    }

    @Test
    fun `images granted is full access`() {
        assertEquals(
            PhotoAccess.Full,
            photoAccessOf(34, mapOf(PERMISSION_READ_MEDIA_IMAGES to true))
        )
    }

    @Test
    fun `storage granted is full access only below Android 13`() {
        assertEquals(
            PhotoAccess.Full,
            photoAccessOf(30, mapOf(PERMISSION_READ_EXTERNAL_STORAGE to true))
        )
        // The permission survives an upgrade in the manifest but stops
        // covering images, so honouring it here would show an empty carousel
        // and call it granted.
        assertEquals(
            PhotoAccess.None,
            photoAccessOf(34, mapOf(PERMISSION_READ_EXTERNAL_STORAGE to true))
        )
    }

    @Test
    fun `the selected-photos grant is partial`() {
        assertEquals(
            PhotoAccess.Partial,
            photoAccessOf(
                34,
                mapOf(
                    PERMISSION_READ_MEDIA_IMAGES to false,
                    PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED to true
                )
            )
        )
    }

    @Test
    fun `full access wins over a partial grant alongside it`() {
        assertEquals(
            PhotoAccess.Full,
            photoAccessOf(
                34,
                mapOf(
                    PERMISSION_READ_MEDIA_IMAGES to true,
                    PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED to true
                )
            )
        )
    }

    @Test
    fun `nothing granted is no access`() {
        assertEquals(PhotoAccess.None, photoAccessOf(34, emptyMap()))
        assertEquals(
            PhotoAccess.None,
            photoAccessOf(34, mapOf(PERMISSION_READ_MEDIA_IMAGES to false))
        )
    }

    @Test
    fun `only partial access offers to widen the selection`() {
        assertTrue(shouldOfferMorePhotos(PhotoAccess.Partial))
        assertFalse(shouldOfferMorePhotos(PhotoAccess.Full))
        assertFalse(shouldOfferMorePhotos(PhotoAccess.None))
    }
}
