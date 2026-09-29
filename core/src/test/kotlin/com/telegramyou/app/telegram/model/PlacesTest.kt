package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    @Test
    fun `a place opens in whatever maps app, pinned and named`() {
        val uri = geoUri(LocationContent(55.7558, 37.6173, title = "Red Square"))
        assertEquals("geo:55.755800,37.617300?q=55.755800%2C37.617300%28Red%20Square%29", uri)
    }

    @Test
    fun `coordinates read with their hemispheres`() {
        assertEquals("55.7558° N, 37.6173° E", coordinatesLabel(LocationContent(55.7558, 37.6173)))
        assertEquals("33.8688° S, 151.2093° E", coordinatesLabel(LocationContent(-33.8688, 151.2093)))
        assertEquals("40.7128° N, 74.0060° W", coordinatesLabel(LocationContent(40.7128, -74.006)))
    }

    @Test
    fun `a contact is on Telegram only with a user behind it`() {
        assertTrue(ContactContent("Lina", "Park", "+100", userId = 11).isOnTelegram)
        assertFalse(ContactContent("Plumber", phoneNumber = "+200").isOnTelegram)
        assertEquals("Lina Park", ContactContent("Lina", "Park", "+100").displayName)
        assertEquals("Plumber", ContactContent("Plumber", phoneNumber = "+200").displayName)
    }
}
