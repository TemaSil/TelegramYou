package com.telegramyou.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FailureTest {

    @Test
    fun `a sentence from the server is kept as it is`() {
        assertEquals(
            "Could not send: Too Many Requests: retry after 17",
            failureText("Could not send", "Too Many Requests: retry after 17")
        )
    }

    @Test
    fun `an error code is turned into words`() {
        assertEquals(
            "Could not save the edit: Message id invalid",
            failureText("Could not save the edit", "MESSAGE_ID_INVALID")
        )
    }

    @Test
    fun `no reason leaves just what failed`() {
        assertEquals("Could not delete", failureText("Could not delete", null))
        assertEquals("Could not delete", failureText("Could not delete", "  "))
    }
}
