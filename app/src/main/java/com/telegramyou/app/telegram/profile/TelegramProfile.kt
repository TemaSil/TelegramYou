package com.telegramyou.app.telegram.profile

/**
 * Changing the account this app is signed in as.
 *
 * Three calls rather than one, because that is what TDLib has: `setName`,
 * `setBio` and `setUsername` are separate methods and fail separately. The
 * username is the one refused most often — it has to be unique across
 * Telegram, and nothing this client knows can tell in advance — so a screen
 * that sent all three together would have no way to say which of them was the
 * problem.
 *
 * Which of the three to call is the caller's decision, made from what actually
 * changed on the form. Sending a field back unchanged is a round trip that can
 * fail for no reason, and this interface deliberately does not hide that by
 * taking the whole form.
 *
 * Each throws with the server's own message when it is refused. There is no
 * transaction across the three: if the name lands and the username is
 * rejected, the name stays changed. Rolling it back would mean another call
 * that could fail in turn, and the screen re-reads the account afterwards
 * anyway, so what it shows is what is true.
 */
interface TelegramProfile {

    /** Both halves at once, because TDLib's `setName` takes both. */
    suspend fun setName(firstName: String, lastName: String)

    /** Empty clears it. */
    suspend fun setBio(bio: String)

    /** Empty gives the username up, which Telegram allows. */
    suspend fun setUsername(username: String)

    /**
     * A new profile photo, from a picked image's Uri. The account is re-read
     * afterwards, so the new picture arrives the way every other change does.
     */
    suspend fun setProfilePhoto(uri: String)

    /**
     * Re-reads the signed-in account and publishes it.
     *
     * Called after a save so that what the screen shows is what the server
     * accepted rather than what was typed — Telegram lower-cases nothing and
     * trims nothing, but it does have the last word, and a client that assumed
     * otherwise would drift from it silently.
     */
    suspend fun refreshMe()
}
