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
import androidx.media3.exoplayer.ExoPlayer
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MusicQueue
import com.telegramyou.app.telegram.model.QueueOrder
import com.telegramyou.app.telegram.model.RepeatMode
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
    val isLoadingMore: Boolean = false
) {
    val track: Track? get() = queue.playing
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

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
                .build()

            override fun isCommandAvailable(command: Int): Boolean =
                availableCommands.contains(command)

            override fun seekToNext() = next()
            override fun seekToNextMediaItem() = next()
            override fun seekToPrevious() = previous()
            override fun seekToPreviousMediaItem() = previous()
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true
        }
    }

    private fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(context)
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
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) advance(auto = true)
                    if (playbackState == Player.STATE_READY) {
                        _state.update { it.copy(durationMs = created.duration.coerceAtLeast(0)) }
                    }
                }
            })
            exo = created
        }

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
        val base = if (sameChat) current else MusicQueue(chatId = track.chatId, sourceTitle = sourceTitle)
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
        val before = _state.value.queue.preceding() ?: run { player.seekTo(0); return }
        playAt(before)
    }

    fun seekTo(fraction: Float) {
        val player = exo ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        _state.update { it.copy(positionMs = player.currentPosition) }
    }

    fun setOrder(order: QueueOrder) = _state.update { it.copy(queue = it.queue.ordered(order)) }

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
        if (queue.isComplete || state.isLoadingMore || queue.chatId == 0L) return
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            val before = queue.tracks.lastOrNull()?.messageId ?: 0L
            val page = runCatching {
                repository.sharedMedia(queue.chatId, SharedMediaKind.Music, before, PAGE)
            }.getOrDefault(emptyList())
            _state.update {
                // Only if it is still that chat's queue: another may have
                // replaced it while the page was on its way.
                if (it.queue.chatId != queue.chatId) {
                    it.copy(isLoadingMore = false)
                } else {
                    it.copy(
                        queue = it.queue.withMore(page.mapNotNull { m -> m.asTrack() }, complete = page.size < PAGE),
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    private fun advance(auto: Boolean) {
        val queue = _state.value.queue
        val next = queue.following(auto)
        when {
            next != null -> playAt(next)
            queue.needsMore() -> {
                // The next track is on the server still: fetch, then go on.
                loadMore()
                scope.launch {
                    repeat(WAIT_FOR_PAGE_STEPS) {
                        delay(WAIT_FOR_PAGE_STEP_MS)
                        _state.value.queue.following(auto)?.let { playAt(it); return@launch }
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
        starting?.cancel()
        _state.update { it.copy(isLoading = true, positionMs = 0, durationMs = track.durationSeconds * 1000L) }
        exo?.pause()
        starting = scope.launch {
            val path = track.path?.takeIf { exists(it) }
                ?: track.fileId?.let { runCatching { repository.downloadFile(it) }.getOrNull() }
            if (path == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            // Kept on the track, so coming back to it does not fetch again.
            _state.update { state ->
                val tracks = state.queue.tracks.map { if (it.messageId == track.messageId) it.copy(path = path) else it }
                state.copy(queue = state.queue.copy(tracks = tracks), isLoading = false)
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
            player.play()
            // The session, and with it the notification, from the first track.
            // Started, not started in the foreground: Media3 moves the service
            // to the foreground itself once something plays, and a service
            // promised to the foreground that then failed to play would be
            // killed for breaking the promise.
            context.startService(Intent(context, PlaybackService::class.java))
            // Near the end of what is loaded, the next page on its way.
            if (_state.value.queue.needsMore(within = PREFETCH_WITHIN)) loadMore()
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
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        const val PAGE = 50
        const val PREFETCH_WITHIN = 3
        const val RESTART_WITHIN_MS = 3_000L
        const val TICK_MS = 250L
        const val WAIT_FOR_PAGE_STEPS = 40
        const val WAIT_FOR_PAGE_STEP_MS = 250L
    }
}
