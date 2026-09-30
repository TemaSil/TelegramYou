package com.telegramyou.app.music

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.telegramyou.app.ui.music.BeatFollower
import com.telegramyou.app.ui.music.loudness16
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The beat of whatever the music player is playing, for the cover to move on
 * (see CoverPulse in :core).
 *
 * Read from the decoded sound on its way to the speaker, which needs no
 * permission: the platform's Visualizer would have asked for the microphone,
 * and a cover that moves is not worth that question.
 *
 * Sound is decoded well ahead of being heard, so each slice's beat is kept
 * with its own time, and [now] answers for the slice being heard at this
 * moment — without that, the cover would move a quarter of a second before
 * the kick it was moving to.
 */
class AudioPulse {
    /** Off while nobody is looking at the cover, or it is switched off. */
    @Volatile var enabled = false

    @Volatile private var heardUs = C.TIME_UNSET
    private val times = LongArray(CAPACITY)
    private val beats = FloatArray(CAPACITY)
    private var count = 0
    private var head = 0
    private val follower = BeatFollower()

    private var sampleRate = 0
    private var channels = 0
    private var pcm16 = false
    private var lastBuffer = C.TIME_UNSET
    private var scratch = ShortArray(0)

    /** The beat being heard now, 0 to 1. */
    fun now(): Float {
        val heard = heardUs
        if (!enabled || heard == C.TIME_UNSET) return 0f
        synchronized(this) {
            // Newest first: the latest slice that has started by now.
            for (k in 0 until count) {
                val i = (head - 1 - k).mod(CAPACITY)
                if (times[i] <= heard) return beats[i]
            }
        }
        return 0f
    }

    internal fun configure(sampleRate: Int, channels: Int, encoding: Int) {
        this.sampleRate = sampleRate
        this.channels = channels
        pcm16 = encoding == C.ENCODING_PCM_16BIT
    }

    internal fun heard(positionUs: Long) {
        heardUs = positionUs
    }

    internal fun analyse(buffer: ByteBuffer, presentationTimeUs: Long) {
        // The sink is handed the same buffer again until it takes all of it.
        if (!enabled || !pcm16 || sampleRate <= 0 || channels <= 0 || presentationTimeUs == lastBuffer) return
        lastBuffer = presentationTimeUs
        val shorts = buffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        val total = shorts.remaining()
        if (scratch.size < total) scratch = ShortArray(total)
        shorts.get(scratch, 0, total)
        val slice = sampleRate / SLICES_PER_SECOND * channels
        if (slice <= 0) return
        val sliceUs = 1_000_000L / SLICES_PER_SECOND
        var at = 0
        var time = presentationTimeUs
        synchronized(this) {
            while (at < total) {
                val end = minOf(total, at + slice)
                val beat = follower.next(loudness16(scratch, at, end), 1000f / SLICES_PER_SECOND)
                times[head] = time
                beats[head] = beat
                head = (head + 1) % CAPACITY
                if (count < CAPACITY) count++
                at = end
                time += sliceUs
            }
        }
    }

    internal fun flush() {
        synchronized(this) {
            count = 0
            head = 0
            follower.reset()
        }
        lastBuffer = C.TIME_UNSET
        heardUs = C.TIME_UNSET
    }

    private companion object {
        /** Slices of 20 ms: short enough for a kick, long enough to measure. */
        const val SLICES_PER_SECOND = 50
        /** Five seconds of them, more than is ever decoded ahead. */
        const val CAPACITY = 256
    }
}

/** The player's audio sink, passing everything on and [AudioPulse] a look. */
@OptIn(UnstableApi::class)
internal class PulseSink(sink: AudioSink, private val pulse: AudioPulse) : ForwardingAudioSink(sink) {
    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val format = audioSinkConfig.format
        pulse.configure(format.sampleRate, format.channelCount, format.pcmEncoding)
        super.configure(audioSinkConfig)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        runCatching { pulse.analyse(buffer, presentationTimeUs) }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        super.getCurrentPositionUs(sourceEnded).also { if (it != AudioSink.CURRENT_POSITION_NOT_SET) pulse.heard(it) }

    override fun flush() {
        pulse.flush()
        super.flush()
    }

    override fun reset() {
        pulse.flush()
        super.reset()
    }
}
