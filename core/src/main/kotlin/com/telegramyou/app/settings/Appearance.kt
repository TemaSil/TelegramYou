package com.telegramyou.app.settings

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
    val shapedAvatars: Boolean = true
)

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
