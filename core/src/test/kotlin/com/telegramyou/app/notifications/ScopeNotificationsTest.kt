package com.telegramyou.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ScopeNotificationsTest {

    @Test
    fun `the main switch says what it covers, or that it is off`() {
        assertEquals("Messages from one person", ScopeNotifications().summaryFor(NotificationScope.PrivateChats))
        assertEquals("Off for groups", ScopeNotifications(enabled = false).summaryFor(NotificationScope.Groups))
    }
}
