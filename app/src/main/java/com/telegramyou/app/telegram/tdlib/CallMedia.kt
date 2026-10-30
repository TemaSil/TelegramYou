package com.telegramyou.app.telegram.tdlib

import android.content.Context
import android.util.Base64
import android.util.Log
import io.github.pytgcalls.ConnectionState
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.p2p.RTCServer
import org.json.JSONArray
import org.json.JSONObject

/**
 * The sound of a call (2.9), through NTgCalls — WebRTC and Telegram's call
 * protocol, prebuilt, LGPL-3.0, linked as its own shared library.
 *
 * TDLib has already done the parts that need the account: it rang, agreed
 * the key and fetched the servers. This hands that over as it is — the key
 * through `skipExchange`, so NTgCalls's own Diffie-Hellman is not used — and
 * carries the two ends' signalling: what NTgCalls emits goes out through
 * [sendSignaling] (TDLib's `sendCallSignalingData`), and what TDLib
 * receives comes in through [receiveSignaling].
 *
 * Keyed by the other person's user id, which is what NTgCalls calls a
 * private call's chat. One call at a time.
 *
 * Null from [create] where the library will not load — an ABI it was not
 * built for — so a call there rings and never connects, rather than taking
 * the app down.
 */
internal class CallMedia private constructor(
    private val calls: NTgCalls,
    private val sendSignaling: (userId: Long, data: String) -> Unit,
    private val onConnection: (userId: Long, connected: Boolean) -> Unit
) {
    private var userId: Long? = null

    init {
        calls.onSignalingData { chatId, data ->
            sendSignaling(chatId, Base64.encodeToString(data, Base64.NO_WRAP))
        }
        calls.onConnectionChange { chatId, info ->
            when (info.state) {
                ConnectionState.CONNECTED -> onConnection(chatId, true)
                ConnectionState.FAILED, ConnectionState.TIMEOUT -> onConnection(chatId, false)
                else -> Unit
            }
        }
    }

    /**
     * Ready for [user]'s call: the microphone and the earpiece opened, so
     * nothing waits on them once the keys arrive. Called on ringing out and
     * on answering, never for a call that only rings here.
     */
    fun prepare(user: Long) {
        if (userId == user) return
        stop()
        try {
            calls.createP2pCall(user)
            val devices = NTgCalls.getMediaDevices()
            val microphone = devices.microphone.firstOrNull()?.metadata ?: DEFAULT_MICROPHONE
            val speaker = devices.speaker.firstOrNull()?.metadata ?: DEFAULT_SPEAKER
            calls.setStreamSources(
                user,
                StreamMode.CAPTURE,
                MediaDescription(AudioDescription(MediaSource.DEVICE, SAMPLE_RATE, 1, microphone, true), null, null, null)
            )
            calls.setStreamSources(
                user,
                StreamMode.PLAYBACK,
                MediaDescription(null, AudioDescription(MediaSource.DEVICE, SAMPLE_RATE, 2, speaker, true), null, null)
            )
            userId = user
        } catch (e: Exception) {
            Log.w(TAG, "prepare: ${e.message}")
            runCatching { calls.stop(user) }
        }
    }

    /**
     * `callStateReady`: the key, the servers and what the other end speaks,
     * as TDLib gives them.
     */
    fun connect(user: Long, isOutgoing: Boolean, ready: JSONObject) {
        prepare(user)
        if (userId != user) return
        try {
            val key = Base64.decode(ready.optString("encryption_key"), Base64.DEFAULT)
            calls.skipExchange(user, key, isOutgoing)
            val versions = ready.optJSONObject("protocol")?.optJSONArray("library_versions")
                ?.let { list -> List(list.length()) { list.optString(it) } }
                .orEmpty()
            calls.connectP2p(
                user,
                serversOf(ready.optJSONArray("servers")),
                versions,
                ready.optBoolean("allow_p2p"),
                ready.optString("custom_parameters").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "connect: ${e.message}")
            onConnection(user, false)
        }
    }

    /** `updateNewCallSignalingData`, from the other end. */
    fun receiveSignaling(data: String) {
        val user = userId ?: return
        try {
            calls.sendSignalingData(user, Base64.decode(data, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.w(TAG, "signaling: ${e.message}")
        }
    }

    fun setMuted(muted: Boolean) {
        val user = userId ?: return
        runCatching { if (muted) calls.mute(user) else calls.unmute(user) }
    }

    fun stop() {
        val user = userId ?: return
        userId = null
        runCatching { calls.stop(user) }
    }

    companion object {
        private const val TAG = "CallMedia"
        private const val SAMPLE_RATE = 48_000
        private const val DEFAULT_MICROPHONE = """{"is_microphone":true}"""
        private const val DEFAULT_SPEAKER = """{"is_microphone":false}"""

        fun create(
            context: Context,
            sendSignaling: (userId: Long, data: String) -> Unit,
            onConnection: (userId: Long, connected: Boolean) -> Unit
        ): CallMedia? = try {
            // WebRTC finds the app through this before it opens a device.
            org.webrtc.ContextUtils.initialize(context.applicationContext)
            CallMedia(NTgCalls(), sendSignaling, onConnection)
        } catch (e: Throwable) {
            Log.w(TAG, "no call media: ${e.message}")
            null
        }

        /**
         * What the media library speaks, for createCall and acceptCall;
         * null where it will not load.
         */
        fun protocol(): JSONObject? = try {
            val p = NTgCalls.getProtocol()
            JSONObject()
                .put("@type", "callProtocol")
                .put("udp_p2p", p.udp_p2p)
                .put("udp_reflector", p.udp_reflector)
                .put("min_layer", p.min_layer)
                .put("max_layer", p.max_layer)
                .put("library_versions", JSONArray(p.library_versions))
        } catch (e: Throwable) {
            null
        }

        /** TDLib's `callServer`s as NTgCalls's: reflectors go as TURN, as Telegram's own clients treat them. */
        private fun serversOf(list: JSONArray?): List<RTCServer> {
            list ?: return emptyList()
            return (0 until list.length()).mapNotNull { index ->
                val server = list.optJSONObject(index) ?: return@mapNotNull null
                val type = server.optJSONObject("type") ?: return@mapNotNull null
                val id = server.optLong("id")
                val ipv4 = server.optString("ip_address")
                val ipv6 = server.optString("ipv6_address")
                val port = server.optInt("port")
                when (type.optString("@type")) {
                    "callServerTypeTelegramReflector" -> RTCServer(
                        id, ipv4, ipv6, port, "", "",
                        true, false, type.optBoolean("is_tcp"),
                        Base64.decode(type.optString("peer_tag"), Base64.DEFAULT)
                    )
                    "callServerTypeWebrtc" -> RTCServer(
                        id, ipv4, ipv6, port,
                        type.optString("username"), type.optString("password"),
                        type.optBoolean("supports_turn"), type.optBoolean("supports_stun"), false,
                        null
                    )
                    else -> null
                }
            }
        }
    }
}
