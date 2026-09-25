package com.telegramyou.app.ui.theme

import androidx.compose.ui.graphics.Color

/** The seed the fallback schemes in Theme.kt are generated from. */
val TealSeed = Color(0xFF1EE2A8)

val AvatarPalette = listOf(
    Color(0xFF1EE2A8),
    Color(0xFFFF6B4A),
    Color(0xFF6C8CFF),
    Color(0xFFFFB020),
    Color(0xFFE26BFF),
    Color(0xFF2AD4FF),
    Color(0xFF8BE55B),
    Color(0xFFFF7A9E)
)

fun avatarColor(seed: Long): Color =
    AvatarPalette[(seed.absoluteValue % AvatarPalette.size).toInt()]

private val Long.absoluteValue: Long
    get() = if (this < 0) -this else this
