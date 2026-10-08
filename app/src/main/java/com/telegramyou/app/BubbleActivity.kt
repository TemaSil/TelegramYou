package com.telegramyou.app

/**
 * A conversation's bubble (2.1): the app's own screen for one chat, in the
 * window Android floats over other apps. An activity of its own because a
 * bubble's must be embeddable and open a new document each time — which
 * the app's own activity, launched from the home screen, must not be. See
 * the manifest, and TelegramForegroundService for the notification that
 * carries it.
 */
class BubbleActivity : MainActivity() {
    override val isBubble: Boolean = true
}
