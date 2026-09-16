package com.telegramyou.app.telegram.model

/**
 * Telegram's waveform format, and how to get bars out of it.
 *
 * A voice note carries its own picture of itself: a run of 5-bit samples,
 * values 0..31, packed back to back with no padding between them and sent as
 * base64. Five bits is not a byte, so a sample straddles byte boundaries more
 * often than not — which is the whole reason this is worth a file and a set of
 * tests rather than a line inside the parser.
 *
 * Bits are packed most-significant first, the same order TDLib and every
 * Telegram client reads them in. Getting that backwards produces a waveform
 * that looks plausible and is wrong, which is the worst kind of wrong: nothing
 * fails, the picture just does not match the sound.
 */
object Waveform {
    /** Samples are 5 bits, so 31 is a full-height bar. */
    const val MAX_SAMPLE = 31

    private const val BITS = 5
}

/**
 * Unpacks 5-bit samples out of [bytes].
 *
 * Trailing bits that cannot make a whole sample are dropped rather than
 * padded: they are the remainder of the last byte, not a quiet moment at the
 * end of the recording.
 */
fun unpackWaveform(bytes: ByteArray): List<Int> {
    val total = (bytes.size * 8) / 5
    if (total <= 0) return emptyList()
    val out = ArrayList<Int>(total)
    for (index in 0 until total) {
        val bitOffset = index * 5
        var value = 0
        for (bit in 0 until 5) {
            val absolute = bitOffset + bit
            val byte = bytes[absolute / 8].toInt() and 0xFF
            // Most-significant bit first, within the byte and across them.
            val bitValue = (byte shr (7 - absolute % 8)) and 1
            value = (value shl 1) or bitValue
        }
        out += value
    }
    return out
}

/**
 * Packs [samples] back into the same format, for a recording of our own.
 *
 * Values outside 0..31 are clamped rather than rejected: the caller is
 * measuring a microphone, and one loud moment should not fail a send.
 */
fun packWaveform(samples: List<Int>): ByteArray {
    if (samples.isEmpty()) return ByteArray(0)
    val bits = samples.size * 5
    // Rounded up: the last byte carries whatever bits are left, padded with
    // zeroes, which unpackWaveform then ignores.
    val out = ByteArray((bits + 7) / 8)
    samples.forEachIndexed { index, raw ->
        val value = raw.coerceIn(0, Waveform.MAX_SAMPLE)
        for (bit in 0 until 5) {
            val bitValue = (value shr (4 - bit)) and 1
            if (bitValue == 1) {
                val absolute = index * 5 + bit
                val position = absolute / 8
                out[position] = (out[position].toInt() or (1 shl (7 - absolute % 8))).toByte()
            }
        }
    }
    return out
}

/**
 * Reduces a waveform to [bars] values between 0 and 1, ready to draw.
 *
 * A voice note can carry a hundred samples or a thousand, and a bubble has
 * room for a few dozen bars — so the picture has to be resampled rather than
 * truncated, which would draw the first two seconds of a minute-long message.
 *
 * Normalised against the loudest sample, not against 31. A quiet recording
 * drawn against the theoretical maximum is a flat line, and the thing a
 * waveform is for is the shape, not the absolute volume.
 */
fun waveformBars(samples: List<Int>, bars: Int): List<Float> {
    if (bars <= 0) return emptyList()
    if (samples.isEmpty()) return List(bars) { 0f }

    val peak = samples.max().coerceAtLeast(1)
    return List(bars) { index ->
        // Each bar averages the slice of the recording it stands for. Slices
        // are computed from the ends rather than by a fixed width, so the last
        // bar reaches the last sample however the division falls.
        val from = index * samples.size / bars
        val to = ((index + 1) * samples.size / bars).coerceAtLeast(from + 1)
        val slice = samples.subList(from, to.coerceAtMost(samples.size))
        if (slice.isEmpty()) 0f else slice.average().toFloat() / peak
    }
}
