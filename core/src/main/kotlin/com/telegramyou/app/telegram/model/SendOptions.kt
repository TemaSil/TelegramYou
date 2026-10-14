package com.telegramyou.app.telegram.model

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * How a message goes, beyond what it says (2.2): [silent] arrives without
 * a sound, [whenOnline] waits on Telegram's side until the other person is
 * next online — both from the send button's menu, as the official client
 * has them.
 *
 * Carried in the coroutine that sends rather than as a parameter of every
 * send call: a text, a quote, an album and a voice message each take their
 * own path to TDLib, and every one of them takes these the same way. The
 * repository wraps the send in it (TelegramRepository.sendMessage) and the
 * TDLib backend reads it off as the request leaves (TdJsonEngine.send); a
 * backend that has no use for it, the demo, never looks.
 */
data class SendOptions(
    val silent: Boolean = false,
    val whenOnline: Boolean = false
) : AbstractCoroutineContextElement(SendOptions) {

    /** Nothing beyond an ordinary send. */
    val isPlain: Boolean get() = !silent && !whenOnline

    companion object Key : CoroutineContext.Key<SendOptions>
}
