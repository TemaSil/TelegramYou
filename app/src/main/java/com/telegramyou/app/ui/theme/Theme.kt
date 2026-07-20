package com.telegramyou.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Expressive motion tokens — bouncy springs inspired by M3 Expressive. */
data class ExpressiveMotion(
    val spatialSpring: androidx.compose.animation.core.SpringSpec<Float> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
    val effectsSpring: androidx.compose.animation.core.SpringSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
    val fabSize: Dp = 64.dp
)

val LocalExpressiveMotion = staticCompositionLocalOf { ExpressiveMotion() }

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

    CompositionLocalProvider(LocalExpressiveMotion provides ExpressiveMotion()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TelegramYouTypography,
            shapes = TelegramYouShapes,
            content = content
        )
    }
}
