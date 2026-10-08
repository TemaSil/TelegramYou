package com.telegramyou.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ScopeNotificationsTest {

    @Test
    fun `the main switch says what it covers, or that it is off`() {
        assertEquals("Messages from one person", ScopeNotifications().summaryFor(NotificationScope.PrivateChats))
        assertEquals("Off for groups", ScopeNotifications(enabled = false).summaryFor(NotificationScope.Groups))
    }

    @Test
    fun `each chat finds its kind, and an unlisted one is a channel`() {
        assertEquals(NotificationScope.PrivateChats, notificationScopeOf(isGroup = false, isChannel = false))
        assertEquals(NotificationScope.Groups, notificationScopeOf(isGroup = true, isChannel = false))
        assertEquals(NotificationScope.Channels, notificationScopeOf(isGroup = false, isChannel = true))
        assertEquals(NotificationScope.Channels, notificationScopeOf(isGroup = false, isChannel = false, inList = false))
    }
}
