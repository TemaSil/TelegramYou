package com.telegramyou.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ApiCredentialsTest {

    // The same arithmetic the build script uses, so the two are checked
    // against each other rather than each against itself.
    private fun mask(plain: String, seed: Int): Pair<String, String> {
        val bytes = plain.toByteArray()
        val mask = Random(seed).nextBytes(bytes.size)
        val masked = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor mask[i].toInt()).toByte() }
        fun ByteArray.hex() = joinToString("") { byte -> "%02x".format(byte) }
        return masked.hex() to mask.hex()
    }

    @Test
    fun `a masked hash comes back as it went in, whatever the mask`() {
        val hash = "0123456789abcdef0123456789abcdef"
        repeat(20) { seed ->
            val (masked, key) = mask(hash, seed)
            assertEquals(hash, unmaskApiHash(masked, key))
        }
    }

    @Test
    fun `the demo build's empty hash stays empty`() {
        assertEquals("", unmaskApiHash("", ""))
    }
}
