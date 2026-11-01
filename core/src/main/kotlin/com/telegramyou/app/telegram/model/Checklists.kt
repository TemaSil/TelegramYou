package com.telegramyou.app.telegram.model

/**
 * A checklist (2.9.1): Telegram's list of tasks in a message, ticked off by
 * whoever may, each tick saying who and when. Making one is Premium on
 * Telegram's side; reading and ticking is everyone's it allows.
 */
data class ChecklistContent(
    val title: String,
    val tasks: List<ChecklistTask>,
    /** Whether this account may tick tasks off here. */
    val canMarkDone: Boolean = false
) {
    val doneCount: Int get() = tasks.count { it.isDone }
}

data class ChecklistTask(
    val id: Int,
    val text: String,
    /** Who ticked it off, when it is; null while it is still to do. */
    val doneBy: String? = null,
    val isDone: Boolean = false
)

/** [taskId] ticked or unticked by this account, before the server says so. */
fun ChecklistContent.toggled(taskId: Int, by: String): ChecklistContent =
    copy(tasks = tasks.map { task ->
        if (task.id != taskId) task
        else if (task.isDone) task.copy(isDone = false, doneBy = null)
        else task.copy(isDone = true, doneBy = by)
    })

/**
 * A checklist written as a title and its tasks, one per line, as the
 * composer takes it: blank lines dropped, at most [MAX_CHECKLIST_TASKS].
 * Null when there is no title or no task.
 */
fun checklistDraft(title: String, lines: String): Pair<String, List<String>>? {
    val tasks = lines.lines().map(String::trim).filter(String::isNotEmpty).take(MAX_CHECKLIST_TASKS)
    val name = title.trim()
    return if (name.isEmpty() || tasks.isEmpty()) null else name to tasks
}

/** Telegram's limit on a checklist's tasks. */
const val MAX_CHECKLIST_TASKS = 30
