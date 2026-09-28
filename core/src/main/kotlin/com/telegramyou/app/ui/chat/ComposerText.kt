package com.telegramyou.app.ui.chat

import java.text.BreakIterator

/**
 * [text] without its last character as a person sees one — the emoji
 * panel's backspace. Not the last `Char`: an emoji is two of them at least,
 * and taking one leaves half a surrogate pair, drawn as a box.
 */
fun dropLastGrapheme(text: String): String {
    if (text.isEmpty()) return text
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(text)
    val start = breaks.preceding(text.length)
    return if (start == BreakIterator.DONE) "" else text.substring(0, start)
}
