package com.telegramyou.app.telegram.tdlib

import com.telegramyou.app.telegram.model.SendOptions
import kotlinx.coroutines.currentCoroutineContext
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
    // Replaced by [reopen]; read from any thread that sends.
    @Volatile private var clientId: Int
    @Volatile private var running = false

    init {
        JsonClient.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
        clientId = JsonClient.createClientId()
    }

    fun start() {
        if (running) return
        running = true
        Receiver.register(clientId, this)
    }

    /**
     * A fresh TDLib instance in place of one that has closed — after a
     * log-out TDLib closes the instance for good, and anything sent to it
     * answers "Request aborted". Updates still arriving from the old one are
     * dropped; answers to what was asked of it are still delivered.
     */
    fun reopen() {
        Receiver.unregister(clientId)
        clientId = JsonClient.createClientId()
        if (running) Receiver.register(clientId, this)
    }

    fun stop() {
        running = false
        Receiver.unregister(clientId)
        Receiver.failAll(this, IllegalStateException("TDLib stopped"))
    }

    fun sendFireAndForget(request: JSONObject) {
        JsonClient.send(clientId, request.toString())
    }

    suspend fun send(request: JSONObject): JSONObject {
        // A message sent under SendOptions takes them with it (2.2): read
        // here, where every way a message leaves passes, rather than at each.
        currentCoroutineContext()[SendOptions]?.let { request.withSendOptions(it) }
        return sendNow(request)
    }

    private suspend fun sendNow(request: JSONObject): JSONObject = suspendCoroutine { cont ->
        val extra = Receiver.expect(this, cont)
        request.put("@extra", extra)
        try {
            JsonClient.send(clientId, request.toString())
        } catch (t: Throwable) {
            Receiver.forget(extra)
            cont.resumeWithException(t)
        }
    }

    /**
     * The one thread that reads from TDLib, for every engine in the process
     * (2.3). `JsonClient.receive` serves every instance at once and may not
     * be called from two threads together — one engine per account, each
     * with a receiver of its own, would have done exactly that. Answers go
     * back to whoever asked, by their `@extra`, unique across engines;
     * updates go to the engine whose instance sent them, by `@client_id`.
     */
    private object Receiver {
        private val engines = ConcurrentHashMap<Int, TdJsonEngine>()
        private val pending = ConcurrentHashMap<String, Pair<TdJsonEngine, Continuation<JSONObject>>>()
        private val extraSeq = AtomicLong(1)
        @Volatile private var thread: Thread? = null

        fun register(clientId: Int, engine: TdJsonEngine) {
            engines[clientId] = engine
            ensureThread()
        }

        fun unregister(clientId: Int) {
            engines.remove(clientId)
        }

        fun expect(engine: TdJsonEngine, cont: Continuation<JSONObject>): String {
            val extra = extraSeq.incrementAndGet().toString()
            pending[extra] = engine to cont
            return extra
        }

        fun forget(extra: String) {
            pending.remove(extra)
        }

        fun failAll(engine: TdJsonEngine, error: Throwable) {
            pending.entries.filter { it.value.first === engine }.forEach { entry ->
                pending.remove(entry.key)?.second?.resumeWithException(error)
            }
        }

        @Synchronized
        private fun ensureThread() {
            if (thread != null) return
            thread = Thread({
                while (true) {
                    val raw = try {
                        JsonClient.receive(1.0)
                    } catch (t: Throwable) {
                        Log.e(TAG, "receive failed", t)
                        null
                    } ?: continue
                    handle(raw)
                }
            }, "tdlib-json-receiver").apply {
                isDaemon = true
                start()
            }
        }

        private fun handle(raw: String) {
            val obj = try {
                JSONObject(raw)
            } catch (t: Throwable) {
                Log.w(TAG, "bad json: $raw")
                return
            }
            val extra = obj.optString("@extra", "")
            if (extra.isNotEmpty()) {
                val waiting = pending.remove(extra)
                if (waiting != null) {
                    val cont = waiting.second
                    if (obj.optString("@type") == "error") {
                        cont.resumeWithException(TdLibException(obj.optInt("code"), obj.optString("message")))
                    } else {
                        cont.resume(obj)
                    }
                    return
                }
            }
            // An update from an instance no engine holds any more — one
            // replaced after a log-out, or an account's engine stopped — is
            // dropped.
            val engine = if (obj.has("@client_id")) {
                engines[obj.optInt("@client_id")]
            } else {
                // Without one, as before there were several: the only engine.
                engines.values.singleOrNull()
            } ?: return
            engine.onUpdate(obj)
        }
    }

    companion object {
        private const val TAG = "TdJsonEngine"

        fun deviceModel(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

        fun systemLanguage(): String =
            java.util.Locale.getDefault().toLanguageTag().ifBlank { "en" }
    }
}

class TdLibException(val code: Int, override val message: String) : Exception("TDLib $code: $message")
