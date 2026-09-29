package com.telegramyou.app.music

import android.content.SharedPreferences
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.ui.chat.VoicePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The voice message playing, for its bubble and for the bar over the screens. */
data class VoiceNow(
    val chatId: Long = 0,
    /** Null when no voice message is under way. */
    val messageId: Long? = null,
    val senderName: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val speed: Float = 1f,
    /** Voice messages still to come after this one. */
    val following: Int = 0
)

/**
 * Voice messages, for the whole app rather than one chat's screen — what
 * 1.6.4 asked of them:
 * - they go on when the chat is left, with a bar over the screens to pause,
 *   change speed or stop them from wherever one is;
 * - five in a row play as five in a row: the chat's voice messages below
 *   the one tapped follow it, each fetched as it comes up;
 * - the speed — 1×, 1.5×, 2× — is remembered from one message and one day
 *   to the next;
 * - the music turns down under them and comes back (VoicePlayer ducks).
 */
class VoicePlayback(
    private val repository: TelegramRepository?,
    private val prefs: SharedPreferences?
) {
    private val player = VoicePlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(VoiceNow(speed = prefs?.getFloat(KEY_SPEED, 1f) ?: 1f))
    val state: StateFlow<VoiceNow> = _state.asStateFlow()

    /** The chat's voice messages after the one playing, oldest first. */
    private var queue: List<ChatMessage> = emptyList()
    private var ticker: Job? = null
    private var fetching: Job? = null

    /** [message], which must be on the phone, now; [after] — the chat's voice messages below it — in turn. */
    fun play(message: ChatMessage, after: List<ChatMessage>) {
        val path = message.voicePath ?: return
        fetching?.cancel()
        queue = after
        start(message, path)
    }

    /** Pause, or carry on where it was. */
    fun toggle() {
        val now = _state.value
        if (now.messageId == null) return
        if (now.isPlaying) {
            player.pause()
            ticker?.cancel()
            _state.update { it.copy(isPlaying = false) }
        } else {
            player.resume()
            player.setSpeed(now.speed)
            _state.update { it.copy(isPlaying = true) }
            tick()
        }
    }

    fun seekTo(fraction: Float) {
        player.seekTo(fraction)
        _state.update { it.copy(progress = fraction.coerceIn(0f, 1f)) }
    }

    /** 1× → 1.5× → 2× → 1×, kept for next time. */
    fun cycleSpeed() {
        val next = VOICE_SPEEDS[(VOICE_SPEEDS.indexOfFirst { it == _state.value.speed } + 1).mod(VOICE_SPEEDS.size)]
        player.setSpeed(next)
        prefs?.edit()?.putFloat(KEY_SPEED, next)?.apply()
        _state.update { it.copy(speed = next) }
    }

    /** Everything stops, the rest of the run with it, and the bar goes. */
    fun stop() {
        fetching?.cancel()
        ticker?.cancel()
        queue = emptyList()
        player.stop()
        _state.update { VoiceNow(speed = it.speed) }
    }

    private fun start(message: ChatMessage, path: String) {
        val speed = _state.value.speed
        val started = player.play(message.id, path, speed) { onEnded() }
        _state.update {
            if (!started) {
                VoiceNow(speed = it.speed)
            } else {
                VoiceNow(
                    chatId = message.chatId,
                    messageId = message.id,
                    senderName = if (message.isOutgoing) "You" else message.senderName.orEmpty(),
                    isPlaying = true,
                    speed = speed,
                    following = queue.size
                )
            }
        }
        if (started) tick()
    }

    /** One ended by itself: the next of the run, fetched if need be, or the end. */
    private fun onEnded() {
        ticker?.cancel()
        val next = queue.firstOrNull()
        if (next == null) {
            stop()
            return
        }
        queue = queue.drop(1)
        fetching = scope.launch {
            val path = next.voicePath
                ?: next.voiceFileId?.let { id -> runCatching { repository?.downloadFile(id) }.getOrNull() }
            if (path == null) {
                stop()
                return@launch
            }
            start(next, path)
            // Heard, as the tapped one is reported by its chat: the sender
            // sees each of the run listened to.
            runCatching { repository?.openMessageContent(next.chatId, next.id) }
        }
    }

    /** The position, ten times a second, while it plays. */
    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (player.isPlaying()) {
                _state.update { it.copy(progress = player.progress()) }
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val TICK_MS = 100L
        val VOICE_SPEEDS = listOf(1f, 1.5f, 2f)
    }
}
