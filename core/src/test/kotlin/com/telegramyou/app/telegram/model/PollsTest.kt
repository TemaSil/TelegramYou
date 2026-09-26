package com.telegramyou.app.telegram.model

import com.telegramyou.app.ui.chat.applying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollsTest {

    private val poll = PollContent(
        id = 1,
        question = "Which?",
        options = listOf(
            PollOption("A", voterCount = 2, percentage = 67),
            PollOption("B", voterCount = 1, percentage = 33),
            PollOption("C")
        ),
        totalVoters = 3
    )

    @Test
    fun `percentages add up to a hundred however the thirds fall`() {
        assertEquals(listOf(34, 33, 33), pollPercentages(listOf(1, 1, 1)))
        assertEquals(listOf(67, 33, 0), pollPercentages(listOf(2, 1, 0)))
        assertEquals(100, pollPercentages(listOf(3, 5, 7, 11)).sum())
    }

    @Test
    fun `nobody voting is all zeros, not a division by zero`() {
        assertEquals(listOf(0, 0), pollPercentages(listOf(0, 0)))
    }

    @Test
    fun `a poll with several answers rounds each against the voters`() {
        // Four people, three of them ticked A and B both.
        assertEquals(listOf(75, 75, 25), pollPercentages(listOf(3, 3, 1), voters = 4))
    }

    @Test
    fun `a vote adds us to the option and to the voters`() {
        val voted = poll.withVote(setOf(2))
        assertEquals(listOf(2, 1, 1), voted.options.map { it.voterCount })
        assertEquals(4, voted.totalVoters)
        assertEquals(listOf(false, false, true), voted.options.map { it.isChosen })
        assertEquals(100, voted.options.sumOf { it.percentage })
        assertTrue(voted.hasVoted)
        assertTrue(voted.showsResults)
        assertFalse(voted.acceptsVotes)
    }

    @Test
    fun `retracting takes back exactly what the vote added`() {
        val back = poll.withVote(setOf(2)).withVote(emptySet())
        assertEquals(poll.options.map { it.voterCount }, back.options.map { it.voterCount })
        assertEquals(poll.totalVoters, back.totalVoters)
        assertFalse(back.hasVoted)
    }

    @Test
    fun `a quiz answer is final, a closed poll takes no votes`() {
        val quiz = poll.copy(isQuiz = true).withVote(setOf(0))
        assertFalse(quiz.canRetract)
        assertTrue(poll.withVote(setOf(0)).canRetract)
        assertFalse(poll.copy(allowsRevoting = false).withVote(setOf(0)).canRetract)
        val closed = poll.copy(isClosed = true)
        assertFalse(closed.acceptsVotes)
        assertTrue("results are for everyone once it closes", closed.showsResults)
    }

    @Test
    fun `labels read as Telegram's do`() {
        assertEquals("Anonymous poll", poll.kindLabel)
        assertEquals("Quiz", poll.copy(isQuiz = true, isAnonymous = false).kindLabel)
        assertEquals("Final results", poll.copy(isClosed = true).kindLabel)
        assertEquals("3 votes", poll.votersLabel)
        assertEquals("1 answer", poll.copy(isQuiz = true, totalVoters = 1).votersLabel)
        assertEquals("No votes yet", poll.copy(totalVoters = 0).votersLabel)
    }

    @Test
    fun `new counts and new buttons reach the message they name`() {
        val message = ChatMessage(id = 7, chatId = 1, text = "Which?", isOutgoing = false, timeLabel = "", poll = poll)
        val window = listOf(message)
        val voted = poll.withVote(setOf(1))
        assertEquals(voted, window.applying(MessageUpdate.PollChanged(1, 7, voted)).single().poll)
        val buttons = listOf(listOf(InlineButton("Next", ButtonAction.Callback("bmV4dA=="))))
        assertEquals(buttons, window.applying(MessageUpdate.ButtonsChanged(1, 7, buttons)).single().inlineKeyboard)
        assertTrue("a message not in the window is left alone",
            window.applying(MessageUpdate.PollChanged(1, 8, voted)) === window)
    }
}
