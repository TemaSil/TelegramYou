package com.telegramyou.app.telegram.model

import com.telegramyou.app.i18n.tr

/**
 * A one-to-one call (2.9), as the call screen draws it.
 *
 * TDLib carries the call's signalling — who rings whom, the keys, the
 * servers — and nothing of its sound; that is the media library's, once
 * [phase] reaches [CallPhase.Connecting]. One call at a time, as Telegram
 * has it: a second ring while one is going is answered busy by the server.
 */
data class CallInfo(
    val id: Int,
    /** The person: in a private chat, the chat's id is theirs. */
    val userId: Long,
    val name: String,
    val photoPath: String? = null,
    val avatarColor: Long = userId,
    val isOutgoing: Boolean,
    val isVideo: Boolean = false,
    val phase: CallPhase,
    /** When the two ends first heard each other, in milliseconds; null before. */
    val connectedAt: Long? = null,
    /** Four emoji both ends see alike, which is how a call shows it is not overheard. */
    val emojis: List<String> = emptyList(),
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    /** Set once [phase] is [CallPhase.Ended]. */
    val end: CallEnd? = null
)

enum class CallPhase {
    /** Asked of the server, which has not answered yet. */
    Requesting,
    /** Ringing at the other end. */
    Ringing,
    /** Ringing here, waiting to be answered or declined. */
    Incoming,
    /** Answered: keys exchanged, the sound on its way. */
    Connecting,
    /** Talking. */
    Active,
    /** Over; [CallEnd] says how. The screen stays a moment to say so. */
    Ended
}

/** How a call came to an end, which is what the screen says as it goes. */
enum class CallEnd {
    HungUp,
    Declined,
    Missed,
    /** The other person is on another call; Telegram's USER_BUSY. */
    Busy,
    Failed
}

/**
 * A TDLib `callState…` read as the screen's [CallPhase].
 *
 * [type] is the state's `@type`; [isReceived] its `is_received`, which in a
 * pending outgoing call is the other phone ringing rather than the server
 * still thinking about it. [mediaConnected] is the media library's word
 * that sound flows, since TDLib's Ready only means the keys are in hand.
 */
fun callPhaseOf(type: String, isOutgoing: Boolean, isReceived: Boolean, mediaConnected: Boolean): CallPhase =
    when (type) {
        "callStatePending" -> when {
            !isOutgoing -> CallPhase.Incoming
            isReceived -> CallPhase.Ringing
            else -> CallPhase.Requesting
        }
        "callStateExchangingKeys" -> CallPhase.Connecting
        "callStateReady" -> if (mediaConnected) CallPhase.Active else CallPhase.Connecting
        else -> CallPhase.Ended
    }

/** A TDLib `callDiscardReason…`, or an error, read as how the call ended. */
fun callEndOf(
    stateType: String,
    reasonType: String?,
    isOutgoing: Boolean,
    wasActive: Boolean,
    error: String? = null
): CallEnd = when {
    stateType == "callStateError" -> if (error?.contains("USER_BUSY") == true) CallEnd.Busy else CallEnd.Failed
    reasonType == "callDiscardReasonDisconnected" -> CallEnd.Failed
    reasonType == "callDiscardReasonMissed" -> if (isOutgoing) CallEnd.Declined else CallEnd.Missed
    reasonType == "callDiscardReasonDeclined" -> CallEnd.Declined
    // Rung, never answered, and put down here: missed, from the other side.
    !wasActive && !isOutgoing && reasonType == "callDiscardReasonHungUp" -> CallEnd.Missed
    else -> CallEnd.HungUp
}

/**
 * The line under the name: what is happening, or how long it has gone on
 * — 0:42, 1:02:05 — as a phone's own dialer shows it.
 */
fun callStatusLabel(phase: CallPhase, elapsedSeconds: Long, end: CallEnd? = null, isVideo: Boolean = false): String =
    when (phase) {
        CallPhase.Requesting -> tr("Calling…", "Вызов…")
        CallPhase.Ringing -> tr("Ringing…", "Звоним…")
        CallPhase.Incoming -> if (isVideo) tr("Incoming video call", "Входящий видеозвонок") else tr("Incoming call", "Входящий звонок")
        CallPhase.Connecting -> tr("Connecting…", "Соединение…")
        CallPhase.Active -> callDuration(elapsedSeconds)
        CallPhase.Ended -> when (end) {
            CallEnd.Declined -> tr("Declined", "Отклонён")
            CallEnd.Missed -> tr("Missed", "Пропущен")
            CallEnd.Busy -> tr("Busy", "Занято")
            CallEnd.Failed -> tr("Call failed", "Звонок не удался")
            CallEnd.HungUp, null -> tr("Call ended", "Звонок завершён")
        }
    }

/** 0:07, 12:40, 1:02:05. */
fun callDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Whether a chat is somebody to call: a person, not a group, a bot or oneself. */
val ChatPreview.isCallable: Boolean
    get() = !isGroup && !isChannel && !isBot && !isSavedMessages
