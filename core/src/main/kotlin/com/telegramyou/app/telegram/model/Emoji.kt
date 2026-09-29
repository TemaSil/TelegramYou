package com.telegramyou.app.telegram.model

/**
 * How many emoji a message is, when it is nothing else: 1 to 3, which
 * Telegram draws large and without a bubble, or 0 for anything more or
 * anything with a letter, a digit or a space in it.
 *
 * Counted by hand rather than with BreakIterator: which version of Unicode
 * that knows depends on the JVM or the Android release, and a family or a
 * flag counted as four would make the same message jumbo on one phone and
 * not on another. Here a joiner, a variation selector, a skin tone or a tag
 * belongs to the emoji before it, and two regional indicators are one flag.
 */
fun jumboEmojiCount(text: String): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    var count = 0
    var joined = false
    var halfFlag = false
    var i = 0
    while (i < trimmed.length) {
        val cp = trimmed.codePointAt(i)
        i += Character.charCount(cp)
        when {
            cp == ZERO_WIDTH_JOINER -> joined = true
            cp == VARIATION_SELECTOR || cp == KEYCAP || cp in SKIN_TONES || cp in TAGS -> Unit
            cp in REGIONAL_INDICATORS -> {
                if (halfFlag) {
                    halfFlag = false
                } else {
                    count++
                    halfFlag = true
                }
            }
            cp.isKeycapBase() -> {
                // A digit, # or * is an emoji only as a keycap: 1️⃣.
                val rest = trimmed.substring(i)
                if (!rest.startsWith("️⃣") && !rest.startsWith("⃣")) return 0
                count++
            }
            cp.isPictographic() -> {
                if (joined) joined = false else count++
                halfFlag = false
            }
            else -> return 0
        }
        if (count > JUMBO_MAX) return 0
    }
    return count
}

private const val JUMBO_MAX = 3
private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR = 0xFE0F
private const val KEYCAP = 0x20E3
private val SKIN_TONES = 0x1F3FB..0x1F3FF
private val TAGS = 0xE0020..0xE007F
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF

private fun Int.isKeycapBase(): Boolean = this in '0'.code..'9'.code || this == '#'.code || this == '*'.code

private fun Int.isPictographic(): Boolean =
    this in 0x1F000..0x1FAFF ||
        this in 0x2600..0x27BF ||
        this in 0x2300..0x23FF ||
        this in 0x2B00..0x2BFF ||
        this in 0x2190..0x21FF ||
        this in 0x25A0..0x25FF ||
        this == 0x00A9 || this == 0x00AE || this == 0x203C || this == 0x2049 ||
        this == 0x2122 || this == 0x2139 || this == 0x3030 || this == 0x303D ||
        this == 0x3297 || this == 0x3299
