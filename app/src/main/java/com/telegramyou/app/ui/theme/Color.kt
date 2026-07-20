package com.telegramyou.app.ui.theme

import androidx.compose.ui.graphics.Color

val TealSeed = Color(0xFF1EE2A8)
val DeepInk = Color(0xFF0B1F1A)
val Mist = Color(0xFFE7F7F1)
val CoralPop = Color(0xFFFF6B4A)
val SoftSand = Color(0xFFF3EFE6)
val NightSurface = Color(0xFF10241F)
val NightCard = Color(0xFF17352D)
val OutgoingBubble = Color(0xFF1EE2A8)
val IncomingBubbleLight = Color(0xFFFFFFFF)
val IncomingBubbleDark = Color(0xFF1C3A32)

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
