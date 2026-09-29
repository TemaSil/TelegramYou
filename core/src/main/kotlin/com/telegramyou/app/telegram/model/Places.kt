package com.telegramyou.app.telegram.model

import java.net.URLEncoder
import java.util.Locale

/**
 * A contact card as Telegram sends it: a name and a number, and — when the
 * number belongs to somebody on Telegram — their user id, which is what lets
 * the card offer a chat with them.
 */
data class ContactContent(
    val firstName: String,
    val lastName: String = "",
    val phoneNumber: String,
    val userId: Long = 0
) {
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val isOnTelegram: Boolean get() = userId != 0L
}

/**
 * A place: its coordinates, and for a venue its name and address. [isLive]
 * for a location being shared as it moves, which this client shows as it
 * stood when the message was drawn.
 */
data class LocationContent(
    val latitude: Double,
    val longitude: Double,
    val title: String = "",
    val address: String = "",
    val isLive: Boolean = false
)

/**
 * The place as a `geo:` link, which Android hands to whichever maps app
 * the person uses — no map is drawn here, and no maps provider is chosen
 * for them. The query repeats the point with a label, which is what makes
 * most maps apps drop a pin rather than only centre the view.
 */
fun geoUri(location: LocationContent): String {
    val lat = String.format(Locale.ROOT, "%.6f", location.latitude)
    val lon = String.format(Locale.ROOT, "%.6f", location.longitude)
    val label = location.title.ifBlank { "Location" }
    val query = URLEncoder.encode("$lat,$lon($label)", "UTF-8").replace("+", "%20")
    return "geo:$lat,$lon?q=$query"
}

/** The coordinates written the way people read them: 55.7558° N, 37.6173° E. */
fun coordinatesLabel(location: LocationContent): String {
    fun part(value: Double, positive: Char, negative: Char) =
        String.format(Locale.ROOT, "%.4f° %c", kotlin.math.abs(value), if (value >= 0) positive else negative)
    return "${part(location.latitude, 'N', 'S')}, ${part(location.longitude, 'E', 'W')}"
}
