package com.telegramyou.app.ui.chat

/**
 * The arithmetic behind swipe-to-reply, kept out of the composable so it can
 * be tested. How the gesture *feels* cannot be — that needs a thumb — but how
 * far the bubble moves and when the reply fires are decisions, not opinions.
 */
object SwipeToReply {

    /** How far the bubble travels before it stops following the finger. */
    const val MAX_OFFSET_DP = 64f

    /** Past this much travel, letting go answers the message. */
    const val TRIGGER_OFFSET_DP = 48f

    /**
     * Beyond [MAX_OFFSET_DP] the bubble keeps moving, at a quarter speed.
     *
     * Stopping dead feels like the gesture broke; following all the way lets a
     * bubble slide off the screen. Resistance says "yes, still listening" and
     * "no, that is as far as this goes" at the same time.
     */
    const val OVERDRAG_RATE = 0.25f
}

/**
 * Where the bubble sits, given how far the finger has actually travelled.
 *
 * Leftward travel returns zero rather than a negative offset: a reply swipe
 * goes one way, and a bubble that can be dragged either way looks like two
 * gestures when there is only one.
 */
fun swipeOffset(
    rawDrag: Float,
    maxOffset: Float = SwipeToReply.MAX_OFFSET_DP
): Float = when {
    rawDrag <= 0f -> 0f
    rawDrag <= maxOffset -> rawDrag
    else -> maxOffset + (rawDrag - maxOffset) * SwipeToReply.OVERDRAG_RATE
}

/**
 * Whether letting go here should answer the message.
 *
 * Measured against the offset the bubble is actually showing, not against raw
 * finger travel — otherwise resistance makes the threshold move, and the
 * gesture fires while the bubble looks short of the mark.
 */
fun shouldTriggerReply(
    offset: Float,
    triggerAt: Float = SwipeToReply.TRIGGER_OFFSET_DP
): Boolean = offset >= triggerAt
