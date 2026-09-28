package com.telegramyou.app.settings

import com.telegramyou.app.ui.theme.Accents

/**
 * Light, dark, or whatever the phone is doing.
 *
 * [System] is the default and is not the same as picking the value the system
 * happens to be on right now: it keeps following, including across the
 * scheduled switch at dusk.
 */
enum class ThemeChoice {
    System,
    Light,
    Dark
}

/**
 * What the user has said about how the app should look.
 *
 * [dynamicColor] is the whole reason this client is called what it is —
 * Material You, the palette Android takes from the wallpaper. It defaults on,
 * and below Android 12 there is nothing to take a palette from, which is why
 * [dynamicColorAvailable] is asked separately rather than folded into this.
 */
data class AppearanceSettings(
    val theme: ThemeChoice = ThemeChoice.System,
    val dynamicColor: Boolean = true,
    /**
     * Whether an avatar takes a shape from Material's library as well as a
     * colour.
     *
     * On by default, because it is the same argument the client is built on:
     * the shape belongs to the person, so somebody is a clover wherever they
     * appear and is recognised before their name is read. It is a switch
     * rather than a rule because a list of circles is what every other
     * messenger looks like, and somebody may want that.
     */
    val shapedAvatars: Boolean = true,
    /**
     * How much larger or smaller than the system's own text this app's is,
     * as a multiple — on top of Android's font size, not instead of it, so
     * somebody who has already made the whole phone larger is not put back.
     */
    val textScale: Float = 1f,
    /**
     * The seed the scheme is built from when it is not the wallpaper's —
     * dynamic colour off, or below Android 12. Teal unless chosen; see
     * Accents.
     */
    val accent: Int = Accents.TEAL,
    /** In the dark theme, black rather than the scheme's near-black page. */
    val pureBlack: Boolean = false,
    /**
     * Each conversation in its own colours, taken from the other side's
     * avatar — the photo's main colour, or the placeholder's colour where
     * there is no photo. The rest of the app keeps the app's colours.
     */
    val chatColorsFromAvatar: Boolean = false,
    /** What the conversation is drawn on; see ChatWallpaper. */
    val chatWallpaper: ChatWallpaper = ChatWallpaper.Gradient,
    /** Which tone the account's own messages are filled with. */
    val outgoingTone: OutgoingTone = OutgoingTone.Accent,
    /** How round a message's corners are, in dp; see BubbleCorners. */
    val bubbleCorners: Int = BubbleCorners.DEFAULT,
    /**
     * Messages' own text size, on top of the app's — the official client's
     * "Message text size", which leaves the rest of the interface alone.
     * The same stops as [textScale]; see TextSize.
     */
    val messageTextScale: Float = 1f
)

/**
 * What a conversation is drawn on. All of it in the theme's own colours —
 * the wallpaper is on the short list of things this client draws by hand,
 * and a picture in somebody else's colours would fight the scheme.
 */
enum class ChatWallpaper(val label: String) {
    /** The surface into a breath of the accent, as it has always been. */
    Gradient("Gradient"),
    /** The surface alone. */
    Plain("Plain"),
    /** The gradient with a quiet grid of dots. */
    Dots("Dots"),
    /** The gradient with soft lines of waves. */
    Waves("Waves")
}

/**
 * The fill of the account's own messages: Material's primary (the loud
 * default), its container (softer, for a calmer chat), or the tertiary
 * container (a second colour against the other side's grey).
 */
enum class OutgoingTone(val label: String) {
    Accent("Accent"),
    Soft("Soft"),
    Tertiary("Tertiary")
}

/**
 * The corner slider's range. The tail — the tighter corner where a run of
 * messages continues — stays as it is unless the corners go below it.
 */
object BubbleCorners {
    const val MIN = 6
    const val MAX = 26
    const val DEFAULT = 20
    const val STEP = 2

    /** Stops between the ends, as a Slider counts them. */
    const val SLIDER_STEPS = (MAX - MIN) / STEP - 1

    /** [value] on a stop, inside the range — what a drag or a stored value settles on. */
    fun settle(value: Float): Int =
        (Math.round((value - MIN) / STEP) * STEP + MIN).coerceIn(MIN, MAX)
}

/**
 * The steps the text size slider stops at. Four rather than a continuous
 * range: two sizes a few hundredths apart are indistinguishable on screen,
 * and a stop with a name is one a person can come back to.
 */
object TextSize {
    val steps: List<Float> = listOf(0.85f, 1f, 1.15f, 1.3f)

    /** The step closest to [scale] — what a stored or dragged value settles on. */
    fun nearest(scale: Float): Float = steps.minBy { kotlin.math.abs(it - scale) }

    fun label(scale: Float): String = when (steps.indexOf(nearest(scale))) {
        0 -> "Small"
        1 -> "Default"
        2 -> "Large"
        else -> "Largest"
    }
}

/**
 * Whether to draw dark, given the choice and what the system is doing.
 *
 * Trivial, and worth having in one place with a test anyway: the version that
 * reads `choice == Dark || isSystemInDarkTheme()` looks right and quietly
 * ignores an explicit Light on a phone in dark mode.
 */
fun isDark(choice: ThemeChoice, systemIsDark: Boolean): Boolean = when (choice) {
    ThemeChoice.System -> systemIsDark
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}

/**
 * Whether the wallpaper palette is available at all.
 *
 * Dynamic colour arrived in Android 12 (API 31). Below it the switch would be
 * a control that changes nothing, so the screen shows it disabled and says
 * why rather than hiding it — hiding it would leave the client's own premise
 * unexplained on the phones where it does not apply.
 */
fun dynamicColorAvailable(sdkInt: Int): Boolean = sdkInt >= 31
