package com.telegramyou.app.ui.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
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
 * The outline of a rounded square whose edges ripple inwards: [size] across,
 * corners of radius [corner], each edge carrying [waves] ripples at most
 * [depth] deep, moved along by [phase] (radians). Points run clockwise from
 * the top edge, as x, y pairs, in the square's own coordinates.
 *
 * The ripples come to nothing at the corners, so a corner keeps its curve
 * and only the flat of each edge moves — "a little at the edges", which is
 * what was asked for. With [depth] 0 it is the plain rounded square. Inwards
 * only, because the cover is drawn inside this shape: an outward ripple would
 * be cut off by the cover's own edge.
 */
fun rippledSquare(
    size: Float,
    corner: Float,
    depth: Float,
    waves: Int = 3,
    phase: Float = 0f,
    edgeSamples: Int = 40,
    cornerSamples: Int = 8
): FloatArray {
    val c = corner.coerceIn(0f, size / 2)
    val flat = size - 2 * c
    val out = ArrayList<Float>((edgeSamples + cornerSamples) * 8)
    // Each side: where its flat starts, which way it runs, which way is in,
    // and the centre and starting angle of the corner that follows it.
    val sides = listOf(
        Side(c, 0f, 1f, 0f, 0f, 1f, size - c, c, -PI / 2),
        Side(size, c, 0f, 1f, -1f, 0f, size - c, size - c, 0.0),
        Side(size - c, size, -1f, 0f, 0f, -1f, c, size - c, PI / 2),
        Side(0f, size - c, 0f, -1f, 1f, 0f, c, c, PI)
    )
    sides.forEachIndexed { index, side ->
        for (i in 0 until edgeSamples) {
            val u = i / edgeSamples.toFloat()
            val along = u * flat
            // Nothing at either end of the flat; ripples in between, each
            // going from the edge to [depth] and back.
            val envelope = sin(PI * u)
            val ripple = 0.5 * (1 - cos(2 * PI * waves * u + phase + index * PI / 2))
            val inset = (depth * envelope * ripple).toFloat()
            out += side.x + side.dx * along + side.nx * inset
            out += side.y + side.dy * along + side.ny * inset
        }
        for (i in 0 until cornerSamples) {
            val a = side.angle + (PI / 2) * (i / cornerSamples.toDouble())
            out += side.cx + (c * cos(a)).toFloat()
            out += side.cy + (c * sin(a)).toFloat()
        }
    }
    return out.toFloatArray()
}

private class Side(
    val x: Float, val y: Float,
    val dx: Float, val dy: Float,
    val nx: Float, val ny: Float,
    val cx: Float, val cy: Float,
    val angle: Double
)
