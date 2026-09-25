package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProxiesTest {

    private val secret = "dd" + "0123456789abcdef".repeat(2)

    @Test
    fun `an MTProto link fills the form`() {
        val draft = parseProxyLink("tg://proxy?server=proxy.example.org&port=443&secret=$secret")
        assertEquals(ProxyDraft("proxy.example.org", "443", ProxyKind.MtProto, secret = secret), draft)
        assertEquals(draft, parseProxyLink("https://t.me/proxy?server=proxy.example.org&port=443&secret=$secret"))
    }

    @Test
    fun `a SOCKS link brings its user and password, decoded`() {
        val draft = parseProxyLink("https://t.me/socks?server=10.0.0.1&port=1080&user=me&pass=p%40ss")
        assertEquals(ProxyDraft("10.0.0.1", "1080", ProxyKind.Socks5, username = "me", password = "p@ss"), draft)
    }

    @Test
    fun `anything else is not a proxy link`() {
        assertNull(parseProxyLink("https://t.me/durov"))
        assertNull(parseProxyLink("https://example.org/proxy?server=a&port=1"))
        assertNull(parseProxyLink("tg://proxy?port=443"))
        assertNull(parseProxyLink("not a link at all"))
        assertNull(parseProxyLink(""))
    }

    @Test
    fun `a draft says what is wrong with it, one thing at a time`() {
        assertEquals("Enter the server", ProxyDraft().problem)
        assertEquals(
            "The port is a number from 1 to 65535",
            ProxyDraft("host", "70000", ProxyKind.Socks5).problem
        )
        assertEquals(
            "An MTProto secret is 32 or more hex digits, or a base64 one",
            ProxyDraft("host", "443", ProxyKind.MtProto, secret = "abc").problem
        )
        assertNull(ProxyDraft("host", "1080", ProxyKind.Socks5).problem)
        assertNull(ProxyDraft("host", "443", ProxyKind.MtProto, secret = secret).problem)
    }

    @Test
    fun `a saved proxy keeps only what its kind uses`() {
        val socks = ProxyDraft(" host ", " 1080 ", ProxyKind.Socks5, "u", "p", secret = secret).toServer()
        assertNotNull(socks)
        assertEquals("host", socks!!.server)
        assertEquals(1080, socks.port)
        assertEquals("", socks.secret)
        val mtproto = ProxyDraft("host", "443", ProxyKind.MtProto, "u", "p", secret).toServer()!!
        assertEquals("", mtproto.username)
        assertEquals(secret, mtproto.secret)
        assertNull(ProxyDraft("host", "x", ProxyKind.Http).toServer())
    }
}
