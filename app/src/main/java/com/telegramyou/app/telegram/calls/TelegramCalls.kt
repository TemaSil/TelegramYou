package com.telegramyou.app.telegram.calls

import com.telegramyou.app.telegram.model.CallInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-to-one calls (2.9): the ring, the answer, the end. The sound itself
 * is not here — TDLib carries only the signalling, and the media library
 * takes over once the keys are agreed.
 */
interface TelegramCalls {
    /** The call going on, ringing or just ended; null when there is none. */
    val call: StateFlow<CallInfo?> get() = NO_CALL

    /** Rings the person whose private chat is [chatId]. */
    suspend fun startCall(chatId: Long, video: Boolean = false) {}

    /** Answers the call ringing here. */
    suspend fun acceptCall() {}

    /** Declines a ringing call, or puts one down. */
    suspend fun endCall() {}

    fun setCallMuted(muted: Boolean) {}

    fun setCallSpeaker(on: Boolean) {}

    /** The ended call taken off the screen. */
    fun dismissCall() {}
}

/** A backend without calls — the tests' fake — never has one. */
private val NO_CALL: StateFlow<CallInfo?> = MutableStateFlow(null)
