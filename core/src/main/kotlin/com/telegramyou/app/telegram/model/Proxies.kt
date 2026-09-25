package com.telegramyou.app.telegram.model

import java.net.URI
import java.net.URLDecoder

/** The three kinds of proxy Telegram speaks through. */
enum class ProxyKind { Socks5, Http, MtProto }

/**
 * A proxy as the settings screen shows it — one TDLib has been given.
 *
 * [id] is TDLib's, and it is what enabling and removing go by. At most one is
 * [isEnabled] at a time; TDLib enforces that, and the screen draws it as a
 * radio choice for the same reason.
 */
data class ProxyServer(
    val id: Int,
    val server: String,
    val port: Int,
    val kind: ProxyKind,
    val username: String = "",
    val password: String = "",
    val secret: String = "",
    val isEnabled: Boolean = false
)

/**
 * A proxy being typed in, before it is one. The port is text because that is
 * what a field holds; [toServer] is where it becomes a number, or does not.
 */
data class ProxyDraft(
    val server: String = "",
    val port: String = "",
    val kind: ProxyKind = ProxyKind.MtProto,
    val username: String = "",
    val password: String = "",
    val secret: String = ""
) {
    /**
     * What is wrong with this draft, or null when it can be saved. One reason
     * at a time, the first that applies, so the form has one line to show.
     */
    val problem: String?
        get() {
            val portNumber = port.trim().toIntOrNull()
            return when {
                server.isBlank() -> "Enter the server"
                server.trim().any { it.isWhitespace() } -> "The server has a space in it"
                portNumber == null || portNumber !in 1..65_535 -> "The port is a number from 1 to 65535"
                kind == ProxyKind.MtProto && !isMtProtoSecret(secret.trim()) ->
                    "An MTProto secret is 32 or more hex digits, or a base64 one"
                else -> null
            }
        }

    /** The proxy this describes, with no id yet — null while [problem] says why. */
    fun toServer(): ProxyServer? {
        if (problem != null) return null
        return ProxyServer(
            id = 0,
            server = server.trim(),
            port = port.trim().toInt(),
            kind = kind,
            username = username.trim().takeIf { kind != ProxyKind.MtProto }.orEmpty(),
            password = password.takeIf { kind != ProxyKind.MtProto }.orEmpty(),
            secret = secret.trim().takeIf { kind == ProxyKind.MtProto }.orEmpty()
        )
    }
}

/**
 * The draft a proxy link describes, or null for anything else.
 *
 * Proxies are handed around as links — `tg://proxy?server=…&port=…&secret=…`
 * for MTProto, `tg://socks?server=…&port=…&user=…&pass=…` for SOCKS5, and the
 * same two under `https://t.me/`. Pasting one fills the whole form, which is
 * how nearly everybody adds a proxy.
 */
fun parseProxyLink(text: String): ProxyDraft? {
    val uri = try {
        URI(text.trim())
    } catch (_: Exception) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    val kindName = when (scheme) {
        "tg" -> uri.host ?: uri.schemeSpecificPart.substringBefore('?')
        "http", "https" -> {
            val host = uri.host?.lowercase() ?: return null
            if (host != "t.me" && host != "telegram.me") return null
            uri.path.trim('/')
        }
        else -> return null
    }
    val kind = when (kindName.lowercase()) {
        "proxy" -> ProxyKind.MtProto
        "socks" -> ProxyKind.Socks5
        else -> return null
    }
    val query = uri.rawQuery ?: return null
    val params = query.split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=', "")
        if (key.isEmpty()) null
        else key to URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
    }.toMap()
    val server = params["server"]?.takeIf { it.isNotBlank() } ?: return null
    val port = params["port"]?.takeIf { it.isNotBlank() } ?: return null
    return ProxyDraft(
        server = server,
        port = port,
        kind = kind,
        username = params["user"].orEmpty(),
        password = params["pass"].orEmpty(),
        secret = params["secret"].orEmpty()
    )
}

/** "MTProto · 1.2.3.4:443" — how a row names its proxy under the server. */
fun ProxyServer.kindLabel(): String = when (kind) {
    ProxyKind.Socks5 -> "SOCKS5"
    ProxyKind.Http -> "HTTP"
    ProxyKind.MtProto -> "MTProto"
}

/**
 * Hex of 32 digits or more (the "dd" and "ee" forms are longer), or the
 * URL-safe base64 some links carry. TDLib is the final judge; this only
 * catches the paste that picked up half the link.
 */
private fun isMtProtoSecret(secret: String): Boolean {
    if (secret.length < 32) return false
    val hex = secret.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    val base64 = secret.all { it.isLetterOrDigit() || it in "-_+/=" }
    return hex || base64
}
