package com.telegramyou.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// The palette below Android 12, and wherever dynamic colour is switched off:
// the scheme Android itself would build from a wallpaper of TealSeed —
// Material's tonal-spot variant, 2021 spec, computed with material-color-
// utilities rather than chosen by hand.
//
// It was chosen by hand before, and it showed: coral for secondary and a
// periwinkle for tertiary beside the teal, three colours from three
// different palettes. The navigation bar's selected pill is
// secondaryContainer, which made it brown on a green screen, and the story
// ring sweeps primary into secondary into tertiary, which made it a
// rainbow. Derived from one seed, the three roles are one family — the same
// relationship dynamic colour gives them — and both read as intended.
//
// To change the colour, change TealSeed and generate both schemes again from
// it (tonal spot, contrast 0, spec 2021) — do not edit single roles.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1D6B50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F2D0),
    onPrimaryContainer = Color(0xFF00513A),
    inversePrimary = Color(0xFF8CD5B4),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF354B41),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FC),
    onTertiaryContainer = Color(0xFF254B5B),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    surfaceTint = Color(0xFF1D6B50),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFECF2ED),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FBF5),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
    surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6DBD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5B4),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA7F2D0),
    inversePrimary = Color(0xFF1D6B50),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA6CCDF),
    onTertiary = Color(0xFF083544),
    tertiaryContainer = Color(0xFF254B5B),
    onTertiaryContainer = Color(0xFFC1E8FC),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceTint = Color(0xFF8CD5B4),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353B37),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceDim = Color(0xFF0F1512)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TelegramYouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

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
