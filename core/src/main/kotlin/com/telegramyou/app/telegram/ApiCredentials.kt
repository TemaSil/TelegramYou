package com.telegramyou.app.telegram

/**
 * Puts the api_hash back together from the two halves the build stored.
 *
 * The build XORs the hash with a random mask and keeps both as hex, so the
 * APK holds no readable 32-character hash for `strings` or a dex search to
 * find (see maskedApiHash in app/build.gradle.kts). This reverses it. It is
 * masking, not encryption: the app has to be able to read the hash to use
 * it, and so can anything that runs the app.
 *
 * In :core, with a test, because a wrong byte here is a client that cannot
 * sign in and says only that the hash is invalid.
 */
fun unmaskApiHash(maskedHex: String, maskHex: String): String {
    require(maskedHex.length == maskHex.length) { "mask and hash differ in length" }
    val masked = maskedHex.hexBytes()
    val mask = maskHex.hexBytes()
    return String(ByteArray(masked.size) { i -> (masked[i].toInt() xor mask[i].toInt()).toByte() })
}

private fun String.hexBytes(): ByteArray =
    ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
