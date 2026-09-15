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

private val LightColors = lightColorScheme(
    primary = TealSeed,
    onPrimary = DeepInk,
    primaryContainer = Color(0xFFB6F5DE),
    onPrimaryContainer = DeepInk,
    secondary = CoralPop,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8CE),
    onSecondaryContainer = Color(0xFF3B1208),
    tertiary = Color(0xFF6C8CFF),
    onTertiary = Color.White,
    background = Mist,
    onBackground = DeepInk,
    surface = Mist,
    onSurface = DeepInk,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2FBF7),
    surfaceContainer = Color(0xFFE8F6F0),
    surfaceContainerHigh = Color(0xFFDCEFE7),
    surfaceContainerHighest = Color(0xFFD0E7DD),
    outline = Color(0xFF6E857C)
)

private val DarkColors = darkColorScheme(
    primary = TealSeed,
    onPrimary = DeepInk,
    primaryContainer = Color(0xFF0F6B52),
    onPrimaryContainer = Color(0xFFB6F5DE),
    secondary = CoralPop,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF7A2E1C),
    onSecondaryContainer = Color(0xFFFFD8CE),
    tertiary = Color(0xFF9BB0FF),
    onTertiary = Color(0xFF101A3A),
    background = DeepInk,
    onBackground = SoftSand,
    surface = NightSurface,
    onSurface = SoftSand,
    surfaceContainerLowest = Color(0xFF081612),
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightCard,
    surfaceContainerHigh = Color(0xFF1E4339),
    surfaceContainerHighest = Color(0xFF255044),
    outline = Color(0xFF8AA399)
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
