package com.telegramyou.app.telegram.tdlib

import android.os.Build
import android.util.Log
import org.drinkless.tdlib.JsonClient
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Thin bridge over the official TDLib JSON interface
 * ([JsonClient](https://github.com/tdlib/td/blob/master/example/java/org/drinkless/tdlib/JsonClient.java)).
 *
 * Flow matches https://core.telegram.org/tdlib/getting-started
 */
class TdJsonEngine(
    private val onUpdate: (JSONObject) -> Unit
) {
    private val clientId: Int
    private val extraSeq = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, Continuation<JSONObject>>()
    @Volatile private var running = false
    private var receiverThread: Thread? = null

    init {
        JsonClient.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
        clientId = JsonClient.createClientId()
    }

    fun start() {
        if (running) return
        running = true
        receiverThread = Thread({
            while (running) {
                val raw = try {
                    JsonClient.receive(1.0)
                } catch (t: Throwable) {
                    Log.e(TAG, "receive failed", t)
                    null
                } ?: continue
                handleIncoming(raw)
            }
        }, "tdlib-json-receiver").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        receiverThread?.interrupt()
        receiverThread = null
        pending.values.forEach {
            it.resumeWithException(IllegalStateException("TDLib stopped"))
        }
        pending.clear()
    }

    fun sendFireAndForget(request: JSONObject) {
        JsonClient.send(clientId, request.toString())
    }

    suspend fun send(request: JSONObject): JSONObject = suspendCoroutine { cont ->
        val extra = extraSeq.incrementAndGet().toString()
        request.put("@extra", extra)
        pending[extra] = cont
        try {
            JsonClient.send(clientId, request.toString())
        } catch (t: Throwable) {
            pending.remove(extra)
            cont.resumeWithException(t)
        }
    }

    private fun handleIncoming(raw: String) {
        val obj = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            Log.w(TAG, "bad json: $raw")
            return
        }
        val extra = obj.optString("@extra", "")
        if (extra.isNotEmpty()) {
            val cont = pending.remove(extra)
            if (cont != null) {
                if (obj.optString("@type") == "error") {
                    cont.resumeWithException(
                        TdLibException(obj.optInt("code"), obj.optString("message"))
                    )
                } else {
                    cont.resume(obj)
                }
                return
            }
        }
        onUpdate(obj)
    }

    companion object {
        private const val TAG = "TdJsonEngine"

        fun deviceModel(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

        fun systemLanguage(): String =
            java.util.Locale.getDefault().toLanguageTag().ifBlank { "en" }
    }
}

class TdLibException(val code: Int, override val message: String) : Exception("TDLib $code: $message")
