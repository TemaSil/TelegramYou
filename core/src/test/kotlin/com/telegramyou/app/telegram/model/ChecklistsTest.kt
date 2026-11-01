package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistsTest {

    private val list = ChecklistContent(
        "Trip",
        listOf(ChecklistTask(1, "Tickets"), ChecklistTask(2, "Hotel", doneBy = "Lina", isDone = true)),
        canMarkDone = true
    )

    @Test
    fun `a task ticks and unticks, saying who`() {
        val ticked = list.toggled(1, "You")
        assertEquals(true, ticked.tasks[0].isDone)
        assertEquals("You", ticked.tasks[0].doneBy)
        assertEquals(2, ticked.doneCount)
        val unticked = ticked.toggled(2, "You")
        assertEquals(false, unticked.tasks[1].isDone)
        assertNull(unticked.tasks[1].doneBy)
    }

    @Test
    fun `a checklist is written as a title and a task a line`() {
        assertEquals("Trip" to listOf("Tickets", "Hotel"), checklistDraft(" Trip ", "Tickets\n\n  Hotel \n"))
        assertNull(checklistDraft("", "Tickets"))
        assertNull(checklistDraft("Trip", "\n \n"))
        assertEquals(MAX_CHECKLIST_TASKS, checklistDraft("Many", (1..40).joinToString("\n") { "t$it" })!!.second.size)
    }
}
