package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentsTest {

    @Test
    fun `no comments yet invites one`() {
        assertEquals("Leave a comment", commentsLabel(0))
    }

    @Test
    fun `one and many are counted`() {
        assertEquals("1 comment", commentsLabel(1))
        assertEquals("27 comments", commentsLabel(27))
    }
}
