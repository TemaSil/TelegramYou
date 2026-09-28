package com.telegramyou.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// The palette below Android 12, and wherever dynamic colour is switched off:
// the scheme Android itself would build from a wallpaper of the chosen accent
// — Material's tonal-spot variant, 2021 spec — computed at run time by
// :core's schemeFromSeed with material-color-utilities.
//
// It was a hand-written teal scheme before, generated once from TealSeed with
// the same settings; PaletteTest holds the run-time teal to those values, so
// the default did not move when the choice of accent arrived. Before that it
// was chosen by hand outright, and showed it: coral for secondary and a
// periwinkle for tertiary beside the teal. Derived from one seed, the three
// roles are one family — the relationship dynamic colour gives them.

/** :core's roles as a Compose scheme. */
fun SchemeColors.toColorScheme(dark: Boolean): ColorScheme {
    fun c(argb: Int) = Color(argb)
    return if (dark) {
        darkColorScheme(
            primary = c(primary), onPrimary = c(onPrimary),
            primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary), onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary), onTertiary = c(onTertiary),
            tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(background), onBackground = c(onBackground),
            surface = c(surface), onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
            surfaceTint = c(surfaceTint),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
            error = c(error), onError = c(onError),
            errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
            outline = c(outline), outlineVariant = c(outlineVariant), scrim = c(scrim),
            surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim),
            surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest), surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest)
        )
    } else {
        lightColorScheme(
            primary = c(primary), onPrimary = c(onPrimary),
            primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary), onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary), onTertiary = c(onTertiary),
            tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(background), onBackground = c(onBackground),
            surface = c(surface), onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
            surfaceTint = c(surfaceTint),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
            error = c(error), onError = c(onError),
            errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
            outline = c(outline), outlineVariant = c(outlineVariant), scrim = c(scrim),
            surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim),
            surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest), surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest)
        )
    }
}

/**
 * Pure black on a finished scheme — the wallpaper's as well as an accent's.
 * The same rule as :core's SchemeColors.pureBlack, which PaletteTest pins:
 * the page and the lowest containers to black, the cards left standing.
 */
fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = surfaceContainerLowest
)

/** The app's scheme for these settings; see TelegramYouTheme. */
@Composable
fun appColorScheme(darkTheme: Boolean, dynamicColor: Boolean, accent: Int, pureBlack: Boolean): ColorScheme {
    val context = LocalContext.current
    val base = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(accent, darkTheme) { schemeFromSeed(accent, darkTheme).toColorScheme(darkTheme) }
    }
    return if (darkTheme && pureBlack) base.pureBlack() else base
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TelegramYouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accent: Int = Accents.TEAL,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = appColorScheme(darkTheme, dynamicColor, accent, pureBlack)

    // MaterialExpressiveTheme, not MaterialTheme. The difference is the motion
    // scheme it publishes: Material components read their spring specs from
    // the theme, so setting it here makes the whole interface move the
    // expressive way without any call site opting in.
    //
    // This is reachable only because material3 is pinned to a 1.5.0 alpha;
    // every stable release keeps these declarations internal. It replaces a
    // hand-rolled ExpressiveMotion holder that approximated the same springs
    // and was read by nothing, so it styled nothing.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = TelegramYouTypography,
        shapes = TelegramYouShapes,
        content = content
    )
}
