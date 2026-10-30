package com.telegramyou.app.ui.chat

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A round video message's ring (2.8.2): how far it has played, drawn round
 * the edge from the top clockwise, and a finger moved along the edge
 * winds it there, as Telegram's own circles do.
 */

/**
 * The point ([dx], [dy]) from the circle's centre, as a share of the way
 * round from twelve o'clock, clockwise: 0 at the top, a quarter at three,
 * a half at six. Screen y grows downwards.
 */
fun ringFraction(dx: Float, dy: Float): Float {
    val angle = atan2(dx.toDouble(), -dy.toDouble())
    val turn = if (angle < 0) angle + 2 * PI else angle
    return (turn / (2 * PI)).toFloat().coerceIn(0f, 1f)
}

/**
 * Whether a finger at ([dx], [dy]) is on the ring rather than the picture:
 * the outer part of the circle, so a drag across the middle still scrolls
 * the conversation.
 */
fun onRing(dx: Float, dy: Float, radius: Float): Boolean =
    radius > 0f && hypot(dx, dy) >= radius * RING_REACH

/** How far out from the centre, as a share of the radius, the ring is held. */
private const val RING_REACH = 0.55f
