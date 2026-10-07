package com.telegramyou.app.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.telegramyou.app.media.TelegramFileDataSource
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MusicQueue
import com.telegramyou.app.telegram.model.QueueOrder
import com.telegramyou.app.telegram.model.RepeatMode
import com.telegramyou.app.telegram.model.SleepTimer
import com.telegramyou.app.telegram.model.resumeFrom
import com.telegramyou.app.ui.theme.seedFromPixels
import android.graphics.BitmapFactory
import android.media.audiofx.AudioEffect
import kotlinx.coroutines.withContext
import com.telegramyou.app.telegram.model.SharedMediaKind
import com.telegramyou.app.telegram.model.Track
import com.telegramyou.app.telegram.model.asTrack
import com.telegramyou.app.telegram.model.nextMusicSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** What the player is doing, for the mini player and the full one. */
data class NowPlaying(
    val queue: MusicQueue = MusicQueue(),
    val isPlaying: Boolean = false,
    /** The playing track's file is being fetched. */
    val isLoading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** More of the chat's music is being paged in. */
    val isLoadingMore: Boolean = false,
    /** The playing track's cover as a Material You seed, once it is here. */
    val coverSeed: Int? = null,
    val sleep: SleepTimer = SleepTimer.Off,
    /** A chat's music being fetched for offline: how far along. */
    val offline: OfflineProgress? = null,
    /** Said once: "Saved to Saved Messages". */
    val notice: String? = null,
    /**
     * Saved Messages, just after a track was saved there: the notice then
     * offers to play from it, the library as the queue.
     */
    val savedTo: Long? = null
) {
    val track: Track? get() = queue.playing
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** A chat's music being downloaded: [done] of [total], for the bar. */
data class OfflineProgress(val chatId: Long, val done: Int, val total: Int)

/**
 * The app's one music player: a chat's music as its queue, played by
 * Media3's ExoPlayer, and shown in the shade, on the lock screen and to
 * headset buttons through a Media3 session ([PlaybackService]).
 *
 * One for the whole app, owned by TelegramYouApp, because music outlives
 * the screen it was started from: the mini player follows the person
 * between screens, and the notification keeps it going with the app closed.
 *
 * The queue is [MusicQueue], appended to and never rebuilt; this class
 * fetches each track's file as it comes up, pages in older tracks as the end
 * of what is loaded nears, and moves on when a track ends.
 */
class MusicPlayer(
    private val context: Context,
    private val repository: TelegramRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var exo: ExoPlayer? = null
    private var ticker: Job? = null
    private var ticks = 0
    private var starting: Job? = null

    /**
     * The player the session shows the system, with Next and Previous
     * answered by the queue: ExoPlayer holds one track at a time, fetched as
     * it comes up, so its own playlist has nothing to skip to.
     */
    val sessionPlayer: Player by lazy {
        object : ForwardingPlayer(player()) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()

            override fun isCommandAvailable(command: Int): Boolean =
                availableCommands.contains(command)

            // From the shade, the lock screen, a headset's or a car's button:
            // logged as theirs, so a skip nobody touched the screen for can
            // be told apart.
            override fun seekToNext() { PlayerLog.add("next, from the system (shade, headset, car)"); next() }
            override fun seekToNextMediaItem() { PlayerLog.add("next, from the system (shade, headset, car)"); next() }
            override fun seekToPrevious() { PlayerLog.add("previous, from the system"); previous() }
            override fun seekToPreviousMediaItem() { PlayerLog.add("previous, from the system"); previous() }
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            // Shuffle and repeat are the queue's, not ExoPlayer's: the
            // shade's and the lock screen's buttons, a watch's and a car's,
            // change the same order the player screen does.
            override fun getShuffleModeEnabled(): Boolean = _state.value.queue.order == QueueOrder.Shuffled
            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) =
                setOrder(if (shuffleModeEnabled) QueueOrder.Shuffled else QueueOrder.Listed)

            override fun getRepeatMode(): Int = when (_state.value.queue.repeat) {
                RepeatMode.Off -> Player.REPEAT_MODE_OFF
                RepeatMode.All -> Player.REPEAT_MODE_ALL
                RepeatMode.One -> Player.REPEAT_MODE_ONE
            }

            override fun setRepeatMode(repeatMode: Int) = setRepeat(
                when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    else -> RepeatMode.Off
                }
            )
        }
    }

    /** The beat of what is playing, for the player's cover; see AudioPulse. */
    val pulse = AudioPulse()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(
        context,
        // Media3's own renderers, with a look at the sound on its way out.
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink? = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
                ?.let { PulseSink(it, pulse) }
        },
        // Telegram's files played as they arrive (TelegramFileDataSource),
        // and an MP3 seeked by an index of its frames rather than by a guess
        // from its bitrate. The guess, on a long file whose header is wrong,
        // landed past the end: "position out of range", twice, and the track
        // was skipped — the owner's log of 3 October, on a radio mix
        // resumed where it was left.
        DefaultMediaSourceFactory(
            TelegramFileDataSource.Factory(context, repository),
            DefaultExtractorsFactory()
                .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
                .setConstantBitrateSeekingEnabled(true)
        )
    )
        // Music, and it pauses for a call and ducks for a navigation prompt.
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        // Headphones out, music stops, as every player on Android does.
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) tick() else ticker?.cancel()
                    if (!isPlaying) keepPosition()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    // The pause and play around each new track are the
                    // player's own, and filled the log with "asked for" on
                    // every skip; they are left out, the track's start is
                    // logged instead.
                    if (changingTrack) return
                    PlayerLog.add((if (playWhenReady) "play" else "pause") + ", " + playWhenReadyReason(reason))
                }

                override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                    if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                        PlayerLog.add("held silent by the system (suppression $reason: audio focus lost for a while, or the output is unsuitable)")
                    }
                }

                // A failure used to stop the music with nothing said. Now it
                // is logged and named, and the track is tried once more from
                // where it was; a second failure goes on to the next.
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val track = _state.value.track
                    PlayerLog.add("error ${error.errorCodeName}: ${error.message} — ${track?.title}")
                    val retry = track != null && retriedFor != track.messageId
                    if (retry) {
                        retriedFor = track.messageId
                        // Past the end of the file is where a seek put it, so
                        // the second try starts from the top, and the place it
                        // was resumed from is forgotten.
                        carryOnFrom = if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
                            forgetPosition()
                            0L
                        } else {
                            created.currentPosition
                        }
                        startCurrent()
                    } else {
                        _state.update { it.copy(notice = "Could not play ${track?.title ?: "the track"}") }
                        advance(auto = true)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        PlayerLog.add("ended at ${created.currentPosition / 1000}s of ${created.duration / 1000}s — ${_state.value.track?.title}")
                        forgetPosition()
                        if (_state.value.sleep == SleepTimer.EndOfTrack) {
                            // The timer was for this track: stop here.
                            _state.update { it.copy(sleep = SleepTimer.Off) }
                        } else {
                            advance(auto = true)
                        }
                    }
                    if (playbackState == Player.STATE_READY) {
                        _state.update { it.copy(durationMs = created.duration.coerceAtLeast(0)) }
                    }
                }
            })
            exo = created
            // Equaliser apps listen for a player's session opening, and
            // attach to it: the platform's audio effects, not ours.
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, created.audioSessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            )
        }

    /** The player's audio session, for the platform's equaliser panel. */
    val audioSessionId: Int get() = exo?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

    /**
     * A track tapped in a chat or in its Music tab: that chat's music
     * becomes the queue — or, when it already is, the queue stays exactly
     * as it is and only the playing track changes. [loaded] is what the
     * caller already has of the chat's music, newest first.
     */
    fun play(message: ChatMessage, sourceTitle: String, loaded: List<ChatMessage> = emptyList(), complete: Boolean = false) {
        val track = message.asTrack() ?: return
        val current = _state.value.queue
        val sameChat = current.chatId == track.chatId && current.tracks.isNotEmpty()
        // A new chat's music is a new queue, played the way the last one was.
        val base = if (sameChat) {
            current
        } else {
            MusicQueue(
                chatId = track.chatId, sourceTitle = sourceTitle, order = current.order, repeat = current.repeat,
                upNext = current.upNext
            )
        }
        val page = loaded.mapNotNull { it.asTrack() }.ifEmpty { listOf(track) }
        var queue = base.withMore(page, complete = complete && loaded.isNotEmpty())
        if (queue.indexOf(track.messageId) < 0) queue = queue.withMore(listOf(track), complete = queue.isComplete)
        queue = queue.startingAt(queue.indexOf(track.messageId))
        if (queue.order == QueueOrder.Shuffled && !sameChat) queue = queue.ordered(QueueOrder.Shuffled)
        _state.update { it.copy(queue = queue) }
        startCurrent()
        // Opened from a single bubble: the rest of the chat's music follows.
        if (loaded.isEmpty()) loadMore()
    }

    /** A row of the queue tapped: that track, the queue untouched. */
    fun playAt(index: Int) {
        keepPosition()
        _state.update { it.copy(queue = it.queue.startingAt(index)) }
        startCurrent()
    }

    fun toggle() {
        val player = exo ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun next() = advance(auto = false)

    /** Back to the start of the track, or — near its start — the track before. */
    fun previous() {
        val player = exo ?: return
        if (player.currentPosition > RESTART_WITHIN_MS) {
            player.seekTo(0)
            return
        }
        val before = _state.value.queue.retreated() ?: run { player.seekTo(0); return }
        go(before)
    }

    /** [queue], with what it plays now, started. */
    private fun go(queue: MusicQueue) {
        keepPosition()
        _state.update { it.copy(queue = queue) }
        startCurrent()
    }

    // ── Up next ──

    /**
     * A track from anywhere to play after this one, before the rest. With
     * nothing playing it simply plays, its chat's music as the queue.
     */
    fun playNext(message: ChatMessage) = lineUp(message) { queue, track -> queue.playNext(track) }

    /** A track from anywhere to the end of Up next. */
    fun addToQueue(message: ChatMessage) = lineUp(message) { queue, track -> queue.addToQueue(track) }

    fun removeUpNext(position: Int) = _state.update { it.copy(queue = it.queue.withoutUpNext(position)) }

    private fun lineUp(message: ChatMessage, put: (MusicQueue, Track) -> MusicQueue) {
        val track = message.asTrack() ?: return
        if (_state.value.track == null) {
            play(message, sourceTitle = "")
            return
        }
        _state.update { it.copy(queue = put(it.queue, track)) }
        prefetchNext()
    }

    fun seekTo(fraction: Float) {
        val player = exo ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        _state.update { it.copy(positionMs = player.currentPosition) }
    }

    fun setOrder(order: QueueOrder) = _state.update { it.copy(queue = it.queue.ordered(order)) }

    fun setRepeat(mode: RepeatMode) = _state.update { it.copy(queue = it.queue.copy(repeat = mode)) }

    fun cycleRepeat() = _state.update {
        val next = RepeatMode.entries[(it.queue.repeat.ordinal + 1) % RepeatMode.entries.size]
        it.copy(queue = it.queue.copy(repeat = next))
    }

    fun cycleSpeed() {
        val speed = nextMusicSpeed(_state.value.speed)
        exo?.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** Everything stops and the mini player goes. */
    fun stop() {
        keepPosition()
        sleeper?.cancel()
        starting?.cancel()
        exo?.stop()
        exo?.clearMediaItems()
        _state.value = NowPlaying(speed = _state.value.speed)
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    /**
     * The next page of the chat's music, older than the oldest loaded —
     * appended, never rebuilding what is there. Called as the end of what is
     * loaded nears, while playing down the list or scrolling the queue.
     */
    fun loadMore() {
        val state = _state.value
        val queue = state.queue
        if (queue.isComplete || state.isLoadingMore || queue.tracks.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            val before = queue.tracks.lastOrNull()?.messageId ?: 0L
            val page = runCatching {
                if (queue.chatId == MY_MUSIC) {
                    // Every chat's: the next page from where the last left off.
                    val cursor = myMusicCursor ?: return@runCatching emptyList()
                    val (found, next) = repository.allMusic(myMusicQuery, cursor, PAGE)
                    myMusicCursor = next
                    found
                } else {
                    repository.sharedMedia(queue.chatId, SharedMediaKind.Music, before, PAGE)
                }
            }.getOrDefault(emptyList())
            _state.update {
                // Only if it is still that chat's queue: another may have
                // replaced it while the page was on its way.
                if (it.queue.chatId != queue.chatId) {
                    it.copy(isLoadingMore = false)
                } else {
                    it.copy(
                        queue = it.queue.withMore(
                            page.mapNotNull { m -> m.asTrack() },
                            complete = if (queue.chatId == MY_MUSIC) myMusicCursor == null else page.size < PAGE
                        ),
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    /**
     * "My music": every chat's tracks as one queue, starting from [message].
     * [loaded] is what the screen has of it; the rest pages in from
     * [cursor] as it is reached — for the same [query], so a queue started
     * from a search goes on with what matched rather than with everything.
     */
    fun playEverywhere(
        message: ChatMessage,
        loaded: List<ChatMessage>,
        cursor: String?,
        query: String = "",
        title: String = "My music"
    ) {
        val track = message.asTrack() ?: return
        myMusicCursor = cursor
        myMusicQuery = query
        val previous = _state.value.queue
        var queue = MusicQueue(
            chatId = MY_MUSIC, sourceTitle = title, order = previous.order, repeat = previous.repeat,
            upNext = previous.upNext
        )
            .withMore(loaded.mapNotNull { it.asTrack() }, complete = cursor == null)
        if (queue.indexOf(track.messageId) < 0) queue = queue.withMore(listOf(track), complete = queue.isComplete)
        queue = queue.startingAt(queue.indexOf(track.messageId))
        if (queue.order == QueueOrder.Shuffled) queue = queue.ordered(QueueOrder.Shuffled)
        keepPosition()
        _state.update { it.copy(queue = queue) }
        startCurrent()
    }

    private var myMusicCursor: String? = null

    /**
     * A collection of the music library — an album, an artist, a playlist,
     * every track — as the queue, from [start] or its first, shuffled if
     * asked. Complete as it is: the library has already found every track.
     */
    fun playCollection(title: String, messages: List<ChatMessage>, start: ChatMessage? = null, shuffle: Boolean = false) {
        // In the order given — an album already as posted — so without the
        // album marks the queue would otherwise reorder by.
        val tracks = messages.mapNotNull { it.asTrack()?.copy(albumId = null) }
        if (tracks.isEmpty()) return
        val previous = _state.value.queue
        var queue = MusicQueue(chatId = LIBRARY, sourceTitle = title, repeat = previous.repeat, upNext = previous.upNext)
            .withMore(tracks, complete = true)
        val first = start?.let { s -> queue.tracks.indexOfFirst { it.messageId == s.id && it.chatId == s.chatId } }
            ?.takeIf { it >= 0 } ?: if (shuffle) tracks.indices.random() else 0
        queue = queue.startingAt(first)
        queue = queue.ordered(if (shuffle) QueueOrder.Shuffled else QueueOrder.Listed)
        keepPosition()
        _state.update { it.copy(queue = queue) }
        startCurrent()
    }

    /** A reaction from the player onto the track's own message, where its sender sees it. */
    fun react(emoji: String) {
        val track = _state.value.track ?: return
        scope.launch {
            val done = runCatching { repository.toggleReaction(track.chatId, track.messageId, emoji) }.isSuccess
            _state.update { it.copy(notice = if (done) "Reacted $emoji to ${track.title}" else "Could not react to it") }
        }
    }

    /**
     * A reply to the playing track's own message, sent from the player (2.0):
     * its sender gets it as a reply to the track, without the chat being
     * opened — what the reactions above do for an emoji.
     */
    fun reply(text: String) {
        val track = _state.value.track ?: return
        val body = text.trim()
        if (body.isEmpty() || track.chatId <= 0) return
        scope.launch {
            val done = runCatching { repository.sendText(track.chatId, body, replyToId = track.messageId) }.isSuccess
            _state.update { it.copy(notice = if (done) "Replied to ${track.title}" else "Could not send the reply") }
        }
    }
    private var myMusicQuery: String = ""

    // ── the seven extras (ROADMAP, 1.6.3) ──

    /** Where long tracks were left, by chat and message; see resumeFrom. */
    private val positions = context.getSharedPreferences("music_positions", Context.MODE_PRIVATE)

    private fun positionKey(track: Track) = "${track.chatId}:${track.messageId}"

    private fun keepPosition() {
        val track = _state.value.track ?: return
        val player = exo ?: return
        if (track.durationSeconds < com.telegramyou.app.telegram.model.RESUME_MIN_SECONDS) return
        positions.edit().putLong(positionKey(track), player.currentPosition).apply()
    }

    private fun forgetPosition() {
        val track = _state.value.track ?: return
        positions.edit().remove(positionKey(track)).apply()
    }

    /** The cover, fetched and turned into the player's colours; see TrackTheme. */
    private fun fetchCover(track: Track) {
        if (track.coverPath == null && track.coverFileId == null) return
        scope.launch {
            val path = track.coverPath ?: track.coverFileId?.let { runCatching { repository.downloadFile(it) }.getOrNull() }
                ?: return@launch
            val seed = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                        ?: return@runCatching null
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    seedFromPixels(pixels)
                }.getOrNull()
            }
            _state.update { state ->
                if (state.track?.messageId != track.messageId) return@update state
                val tracks = state.queue.tracks.map { if (it.messageId == track.messageId) it.copy(coverPath = path) else it }
                state.copy(queue = state.queue.copy(tracks = tracks), coverSeed = seed)
            }
        }
    }

    private var sleeper: Job? = null

    /** The sleep timer: off, after so many minutes, or at the end of this track. */
    fun setSleep(timer: SleepTimer) {
        sleeper?.cancel()
        _state.update { it.copy(sleep = timer) }
        if (timer.minutes > 0) {
            sleeper = scope.launch {
                delay(timer.minutes * 60_000L)
                exo?.pause()
                _state.update { it.copy(sleep = SleepTimer.Off, notice = "Sleep timer: music stopped") }
            }
        }
    }

    /**
     * Saved Messages as the library: the track forwarded there, where it
     * stays whatever happens to the chat it came from.
     */
    fun saveToLibrary() {
        val track = _state.value.track ?: return
        scope.launch {
            val saved = repository.chats.value.firstOrNull { it.isSavedMessages }?.id
                ?: repository.authState.value.me?.id
                ?: return@launch
            val done = runCatching {
                repository.forwardMessages(track.chatId, listOf(track.messageId), saved, false)
            }.isSuccess
            _state.update {
                it.copy(
                    notice = if (done) "Saved to Saved Messages" else "Could not save it",
                    // Offered only from another chat's queue: from Saved
                    // Messages' own, the copy is already where it plays.
                    savedTo = saved.takeIf { done && it != track.chatId }
                )
            }
        }
    }

    /**
     * Saved Messages' music as the queue — the library, newest first, so the
     * track just saved is the one playing. It is the same file, so it
     * carries on from where it was rather than starting over.
     */
    fun playSaved(chatId: Long) {
        val playing = _state.value.track
        val at = exo?.currentPosition ?: 0L
        scope.launch {
            val page = runCatching { repository.sharedMedia(chatId, SharedMediaKind.Music, 0L, PAGE) }
                .getOrDefault(emptyList())
            val first = page.firstOrNull() ?: return@launch
            if (playing != null && first.asTrack()?.fileId == playing.fileId) carryOnFrom = at
            keepPosition()
            play(first, "Saved Messages", page, complete = page.size < PAGE)
        }
    }

    /** Where the next track starts, once, when it is the one that was playing. */
    private var carryOnFrom: Long? = null

    /**
     * Every track of a chat fetched onto the phone, for listening without a
     * connection: paged through to the first, then downloaded one by one,
     * the progress in [NowPlaying.offline].
     */
    fun downloadChat(chatId: Long) {
        if (_state.value.offline != null) return
        scope.launch {
            val all = mutableListOf<ChatMessage>()
            var before = 0L
            while (true) {
                val page = runCatching { repository.sharedMedia(chatId, SharedMediaKind.Music, before, PAGE) }
                    .getOrDefault(emptyList())
                all += page
                if (page.size < PAGE) break
                before = page.last().id
            }
            val files = all.mapNotNull { it.asTrack()?.takeIf { track -> track.path == null && track.fileId != null } }
                .distinctBy { it.fileId }
            _state.update { it.copy(offline = OfflineProgress(chatId, 0, files.size)) }
            files.forEachIndexed { index, track ->
                // Into Downloads, where each can be paused, and where they
                // are listed afterwards.
                runCatching { repository.downloadToList(track.chatId, track.messageId, track.fileId!!) }
                _state.update { it.copy(offline = OfflineProgress(chatId, index + 1, files.size)) }
            }
            _state.update {
                it.copy(
                    offline = null,
                    notice = if (files.isEmpty()) "All of it is on the phone already" else "${files.size} tracks downloaded"
                )
            }
        }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null, savedTo = null) }

    private fun advance(auto: Boolean) {
        val queue = _state.value.queue
        val next = queue.advanced(auto)
        PlayerLog.add(
            (if (auto) "on to the next by itself" else "next, asked for") +
                (next?.playing?.let { " → ${it.title}" } ?: if (queue.needsMore()) " → fetching more" else " → end of the queue, pausing")
        )
        when {
            next != null -> go(next)
            queue.needsMore() -> {
                // The next track is on the server still: fetch, then go on.
                loadMore()
                scope.launch {
                    repeat(WAIT_FOR_PAGE_STEPS) {
                        delay(WAIT_FOR_PAGE_STEP_MS)
                        _state.value.queue.advanced(auto)?.let { go(it); return@launch }
                        if (!_state.value.isLoadingMore && _state.value.queue.isComplete) return@launch
                    }
                }
            }
            else -> exo?.pause()
        }
    }

    /** The playing track's file, fetched if need be, into the player. */
    private fun startCurrent() {
        val track = _state.value.queue.playing ?: return
        val carried = carryOnFrom.also { carryOnFrom = null }
        starting?.cancel()
        _state.update { it.copy(isLoading = true, positionMs = 0, durationMs = track.durationSeconds * 1000L, coverSeed = null) }
        fetchCover(track)
        quietly { exo?.pause() }
        val onPhone = track.path?.let { exists(it) } == true
        // Not on the phone: played as it downloads (1.9) where the backend
        // can, rather than fetched whole first — the log showed tracks
        // skipped past while they were still "fetching".
        val streamed = !onPhone && track.fileId != null && repository.canStream
        PlayerLog.add("start ${track.title}" + when {
            onPhone -> " (on the phone)"
            streamed -> " (streaming)"
            else -> " (fetching)"
        })
        starting = scope.launch {
            // Once more after a moment if the fetch fails: a download that
            // failed used to leave the player silent with nothing said, which
            // read as music stopping by itself.
            var path = when {
                onPhone -> track.path
                streamed -> TelegramFileDataSource.uriOf(track.fileId!!).toString()
                else -> track.fileId?.let { runCatching { repository.downloadFile(it) }.getOrNull() }
            }
            if (path == null && track.fileId != null) {
                PlayerLog.add("fetch failed, trying again — ${track.title}")
                delay(FETCH_RETRY_MS)
                path = runCatching { repository.downloadFile(track.fileId!!) }.getOrNull()
            }
            if (path == null) {
                PlayerLog.add("could not fetch ${track.title}; stopped")
                _state.update { it.copy(isLoading = false, notice = "Could not load ${track.title}") }
                return@launch
            }
            // Kept on the track, so coming back to it does not fetch again —
            // a file, not a stream's address, which is good only while it plays.
            _state.update { state ->
                state.copy(
                    queue = if (streamed) state.queue else state.queue.withPath(track.messageId, path),
                    isLoading = false
                )
            }
            val player = player()
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(track.messageId.toString())
                    .setUri(if ("://" in path) Uri.parse(path) else Uri.fromFile(File(path)))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.performer.ifBlank { null })
                            .setAlbumTitle(_state.value.queue.sourceTitle.ifBlank { null })
                            .build()
                    )
                    .build()
            )
            player.playbackParameters = PlaybackParameters(_state.value.speed)
            player.prepare()
            // A long track carries on where it was left; see resumeFrom.
            (carried ?: resumeFrom(track.durationSeconds, positions.getLong(positionKey(track), 0)))?.let(player::seekTo)
            quietly { player.play() }
            // The session, and with it the notification, from the first track.
            // Started, not started in the foreground: Media3 moves the service
            // to the foreground itself once something plays, and a service
            // promised to the foreground that then failed to play would be
            // killed for breaking the promise.
            // Guarded, because this also runs when a track ends and the next
            // one starts with the screen off: should the service have gone by
            // then, Android refuses to start one for an app in the
            // background, and that refusal is an exception. Unguarded it
            // closed the app mid-listen; guarded, the music plays on.
            try {
                context.startService(Intent(context, PlaybackService::class.java))
            } catch (_: IllegalStateException) {
            } catch (_: SecurityException) {
            }
            // Near the end of what is loaded, the next page on its way.
            if (_state.value.queue.needsMore(within = PREFETCH_WITHIN)) loadMore()
            prefetchNext()
        }
    }

    private var prefetching: Job? = null

    /** Set while the player itself pauses and plays around a new track; see onPlayWhenReadyChanged. */
    private var changingTrack = false

    private inline fun quietly(action: () -> Unit) {
        changingTrack = true
        try {
            action()
        } finally {
            changingTrack = false
        }
    }

    /** The track a failed playback was retried for, so it is retried once. */
    private var retriedFor: Long? = null

    /**
     * The next track's file fetched while this one plays, so one ends and
     * the next begins without a silence spent downloading — Telegram's
     * player waits for each in turn.
     */
    private fun prefetchNext() {
        val playing = _state.value.track ?: return
        // While this one streams it has the connection to itself: a fetch of
        // the next, asked for later at the same priority, would go first.
        if (playing.path?.let { exists(it) } != true && repository.canStream) return
        val next = _state.value.queue.advanced(auto = true)?.playing ?: return
        val fileId = next.fileId ?: return
        if (next.messageId == playing.messageId || next.path?.let { exists(it) } == true) return
        prefetching?.cancel()
        prefetching = scope.launch {
            val path = runCatching { repository.downloadFile(fileId) }.getOrNull() ?: return@launch
            _state.update { it.copy(queue = it.queue.withPath(next.messageId, path)) }
        }
    }

    private fun exists(path: String): Boolean = "://" in path || File(path).exists()

    /** The position, a few times a second while something plays. */
    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val player = exo ?: break
                _state.update {
                    it.copy(positionMs = player.currentPosition, durationMs = player.duration.coerceAtLeast(it.durationMs))
                }
                if (++ticks % KEEP_EVERY_TICKS == 0) keepPosition()
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        /** The queue's chat id for "My music", every chat's tracks. */
        const val MY_MUSIC = -1L
        /** The queue's chat id for a collection of the music library. */
        const val LIBRARY = -2L
        const val PAGE = 50
        const val PREFETCH_WITHIN = 3
        const val RESTART_WITHIN_MS = 3_000L
        const val TICK_MS = 250L
        /** Where a long track is, written down every ten seconds of it. */
        const val KEEP_EVERY_TICKS = 40
        const val WAIT_FOR_PAGE_STEPS = 40
        const val WAIT_FOR_PAGE_STEP_MS = 250L
        /** How long before a failed fetch of a track is tried again. */
        const val FETCH_RETRY_MS = 2_000L
    }
}

/** Media3's reason for a play or a pause, in words for the player log. */
private fun playWhenReadyReason(reason: Int): String = when (reason) {
    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "asked for (this app, the shade or a button)"
    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "another app took the audio (a call, a video, navigation)"
    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "headphones or Bluetooth disconnected"
    Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "from elsewhere (a watch, a car, another device)"
    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "the track ended"
    Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "held silent too long by the system"
    else -> "reason $reason"
}
