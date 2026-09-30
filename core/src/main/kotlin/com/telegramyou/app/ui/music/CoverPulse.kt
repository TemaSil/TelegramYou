package com.telegramyou.app.ui.music

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The beat in a stream of loudness, for the player's cover to move on.
 *
 * Fed the loudness of each short slice of audio as it is decoded, it answers
 * 0 to 1: how much of a hit this slice is. Loudness alone would not do — a
 * loud track would hold the cover at its most moved and a quiet one never
 * move it. What moves it is loudness rising above what the music has just
 * been, measured against the biggest such rise of the last few seconds, so
 * a quiet ballad and a loud dance track both pulse on their own beats. A hit
 * then falls away over a fifth of a second, which is what reads as a pulse
 * rather than a flicker.
 */
class BeatFollower {
    private var recent = 0f
    private var biggest = 0f
    private var pulse = 0f

    /** The next slice: its [loudness] (RMS, 0 to 1) and how long it lasts. */
    fun next(loudness: Float, sliceMs: Float): Float {
        recent += (loudness - recent) * (1f - exp(-sliceMs / RECENT_MS))
        val rise = max(0f, loudness - recent)
        biggest = max(rise, biggest * exp(-sliceMs / BIGGEST_MS))
        // Near silence nothing is a hit, however it compares with nothing.
        val hit = if (biggest > FLOOR) rise / biggest else 0f
        pulse = max(hit, pulse * exp(-sliceMs / FALL_MS))
        return pulse
    }

    fun reset() {
        recent = 0f
        biggest = 0f
        pulse = 0f
    }

    private companion object {
        const val RECENT_MS = 350f
        const val BIGGEST_MS = 3_000f
        const val FALL_MS = 200f
        const val FLOOR = 0.01f
    }
}

/** The RMS of 16-bit samples, 0 to 1. */
fun loudness16(samples: ShortArray, from: Int = 0, to: Int = samples.size): Float {
    if (to <= from) return 0f
    var sum = 0.0
    for (i in from until to) {
        val s = samples[i] / 32768.0
        sum += s * s
    }
    return sqrt(sum / (to - from)).toFloat()
}

/**
 * The vertices of a square with [scallops] scallops along each edge, as x, y
 * pairs from -1 to 1, clockwise from the top-left corner — the cookie the
 * player's cover breathes into on the beat (PulsingCover). Every edge runs
 * corner, bulge, dent, bulge, … dent, bulge, and the dents sit [depth] in
 * from the edge; the corners and the bulges stay on it.
 *
 * With [depth] 0 the dents are on the edge too and it is the plain square,
 * with exactly the same vertices in the same order. That is the point: the
 * rounded square and the cookie are one outline at two depths, so morphing
 * between them moves points rather than rebuilding a shape.
 */
fun scallopedSquare(scallops: Int, depth: Float): FloatArray {
    val perEdge = scallops * 2
    val out = FloatArray(4 * perEdge * 2)
    // Each edge: where it starts, which way it runs, which way is in.
    val edges = arrayOf(
        floatArrayOf(-1f, -1f, 1f, 0f, 0f, 1f),
        floatArrayOf(1f, -1f, 0f, 1f, -1f, 0f),
        floatArrayOf(1f, 1f, -1f, 0f, 0f, -1f),
        floatArrayOf(-1f, 1f, 0f, -1f, 1f, 0f)
    )
    var i = 0
    for (edge in edges) {
        for (k in 0 until perEdge) {
            val along = 2f * k / perEdge
            // Odd steps are the dents; the corner (k = 0) and the even steps
            // are on the edge.
            val inset = if (k % 2 == 1) depth else 0f
            out[i++] = edge[0] + edge[2] * along + edge[4] * inset
            out[i++] = edge[1] + edge[3] * along + edge[5] * inset
        }
    }
    return out
}
