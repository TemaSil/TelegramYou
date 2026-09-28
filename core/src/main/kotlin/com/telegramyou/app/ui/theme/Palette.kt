package com.telegramyou.app.ui.theme

import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score

/**
 * Material's colour roles, as ARGB, for one seed and one brightness.
 *
 * What Android computes from a wallpaper, computed here from any colour:
 * Material's tonal-spot variant, the 2021 spec, standard contrast —
 * the same settings the hand-written teal fallback in Theme.kt was made
 * with, which the tests hold this to. Plain ints rather than Compose
 * colours, so the rule lives in :core and is tested on the JVM; :app turns
 * them into a ColorScheme.
 *
 * Built with material-color-utilities (the Kotlin port, com.materialkolor,
 * Apache 2.0) — Google's own library, the one the platform uses.
 */
data class SchemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceTint: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
    val scrim: Int,
    val surfaceBright: Int,
    val surfaceDim: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainerLowest: Int
)

/** The whole scheme for [seed] (ARGB), light or [dark]. */
fun schemeFromSeed(seed: Int, dark: Boolean): SchemeColors {
    val scheme: DynamicScheme = SchemeTonalSpot(
        Hct.fromInt(seed),
        dark,
        0.0,
        ColorSpec.SpecVersion.SPEC_2021,
        DynamicScheme.Platform.PHONE
    )
    val roles = MaterialDynamicColors()
    fun argb(color: com.materialkolor.dynamiccolor.DynamicColor) = color.getArgb(scheme)
    return SchemeColors(
        primary = argb(roles.primary()),
        onPrimary = argb(roles.onPrimary()),
        primaryContainer = argb(roles.primaryContainer()),
        onPrimaryContainer = argb(roles.onPrimaryContainer()),
        inversePrimary = argb(roles.inversePrimary()),
        secondary = argb(roles.secondary()),
        onSecondary = argb(roles.onSecondary()),
        secondaryContainer = argb(roles.secondaryContainer()),
        onSecondaryContainer = argb(roles.onSecondaryContainer()),
        tertiary = argb(roles.tertiary()),
        onTertiary = argb(roles.onTertiary()),
        tertiaryContainer = argb(roles.tertiaryContainer()),
        onTertiaryContainer = argb(roles.onTertiaryContainer()),
        background = argb(roles.background()),
        onBackground = argb(roles.onBackground()),
        surface = argb(roles.surface()),
        onSurface = argb(roles.onSurface()),
        surfaceVariant = argb(roles.surfaceVariant()),
        onSurfaceVariant = argb(roles.onSurfaceVariant()),
        surfaceTint = argb(roles.surfaceTint()),
        inverseSurface = argb(roles.inverseSurface()),
        inverseOnSurface = argb(roles.inverseOnSurface()),
        error = argb(roles.error()),
        onError = argb(roles.onError()),
        errorContainer = argb(roles.errorContainer()),
        onErrorContainer = argb(roles.onErrorContainer()),
        outline = argb(roles.outline()),
        outlineVariant = argb(roles.outlineVariant()),
        scrim = argb(roles.scrim()),
        surfaceBright = argb(roles.surfaceBright()),
        surfaceDim = argb(roles.surfaceDim()),
        surfaceContainer = argb(roles.surfaceContainer()),
        surfaceContainerHigh = argb(roles.surfaceContainerHigh()),
        surfaceContainerHighest = argb(roles.surfaceContainerHighest()),
        surfaceContainerLow = argb(roles.surfaceContainerLow()),
        surfaceContainerLowest = argb(roles.surfaceContainerLowest())
    )
}

/**
 * Pure black for OLED: the dark scheme with its page and the lowest
 * containers taken to black, and the higher containers — cards, bubbles,
 * fields — left as they are, so what sits on the page still reads as
 * standing on it.
 */
fun SchemeColors.pureBlack(): SchemeColors = copy(
    background = BLACK,
    surface = BLACK,
    surfaceDim = BLACK,
    surfaceContainerLowest = BLACK,
    surfaceContainerLow = surfaceContainerLowest
)

/**
 * The colour a picture is mostly about, the way Android picks one from a
 * wallpaper: quantize, then score for a colour that is both common and
 * lively enough to be a seed. Null for a picture with nothing to go on — a
 * grey one — so the caller keeps its own colour.
 *
 * [pixels] are ARGB, as a Bitmap's getPixels gives them; a small bitmap is
 * plenty, and much faster.
 */
fun seedFromPixels(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    val counts = QuantizerCelebi.quantize(pixels, MAX_COLORS)
    return Score.score(counts, 1, null, true).firstOrNull()
}

/**
 * The accents offered when colour is not taken from the wallpaper, teal
 * first as the default. Seeds, not finished colours: each becomes a whole
 * scheme through [schemeFromSeed], so how bright or muted they look here
 * does not matter much — the tonal-spot variant evens them out.
 */
object Accents {
    const val TEAL = 0xFF1EE2A8.toInt()

    val all: List<Pair<String, Int>> = listOf(
        "Teal" to TEAL,
        "Blue" to 0xFF4285F4.toInt(),
        "Violet" to 0xFF7C4DFF.toInt(),
        "Pink" to 0xFFE91E63.toInt(),
        "Red" to 0xFFE53935.toInt(),
        "Orange" to 0xFFFF8A00.toInt(),
        "Amber" to 0xFFFFC107.toInt(),
        "Green" to 0xFF4CAF50.toInt()
    )

    /** The name of [seed] if it is one of the offered accents. */
    fun nameOf(seed: Int): String? = all.firstOrNull { it.second == seed }?.first
}

private const val BLACK = 0xFF000000.toInt()

/** Colours the quantizer boils a picture down to before scoring. */
private const val MAX_COLORS = 128
