package com.telegramyou.app.telegram

/**
 * What the person can currently see, for the benefit of the notification
 * service.
 *
 * A global, which wants justifying. The service and the activity are separate
 * Android components in one process with no binding between them, and the
 * only question being asked is "is this chat in front of the person right
 * now" — a fact about the process, true or false at an instant, with no
 * history worth keeping. Binding the service to the activity, or routing this
 * through the repository, would be machinery around two booleans.
 *
 * Written from the main thread by the activity and read from the service's
 * coroutine, hence `@Volatile`: without it the reader can sit on a stale
 * value and notify for the chat being read.
 *
 * The cost is that this is process state, so it is wrong after process death
 * until the activity next resumes — which is the safe direction. It reads as
 * "nothing is on screen", and the worst case is a notification for a chat
 * that is, rather than silence for one that is not.
 */
object AppVisibility {
    @Volatile
    var isInForeground: Boolean = false

    /** The chat on screen, or null when the conversation screen is not up. */
    @Volatile
    var openChatId: Long? = null
}
