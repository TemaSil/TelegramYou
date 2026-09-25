package com.telegramyou.app.telegram.model

/**
 * One place this account is signed in — TDLib's `session`, reduced to what
 * the devices screen shows and acts on.
 */
data class ActiveSession(
    val id: Long,
    /** This app, on this phone. It cannot be ended from here, only logged out of. */
    val isCurrent: Boolean,
    val kind: DeviceKind,
    val applicationName: String,
    val applicationVersion: String,
    val isOfficialApplication: Boolean,
    val deviceModel: String,
    val platform: String,
    val systemVersion: String,
    /** Epoch seconds. */
    val lastActiveDate: Long,
    val ipAddress: String,
    val location: String,
    /** Signed in but still waiting for the two-step password. */
    val isPasswordPending: Boolean = false
)

/**
 * What kind of thing a session runs on, which picks its icon. TDLib names
 * seventeen; they come down to these.
 */
enum class DeviceKind { Android, Iphone, Ipad, Mac, Windows, Linux, Browser, Console, Unknown }

/** TDLib's `SessionDeviceType` name to a [DeviceKind]. */
fun deviceKindOf(tdType: String): DeviceKind = when (tdType.removePrefix("sessionDeviceType")) {
    "Android" -> DeviceKind.Android
    "Iphone" -> DeviceKind.Iphone
    "Ipad" -> DeviceKind.Ipad
    "Mac", "Apple" -> DeviceKind.Mac
    "Windows" -> DeviceKind.Windows
    "Linux", "Ubuntu" -> DeviceKind.Linux
    "Chrome", "Edge", "Firefox", "Opera", "Safari", "Brave", "Vivaldi" -> DeviceKind.Browser
    "Xbox" -> DeviceKind.Console
    else -> DeviceKind.Unknown
}

/** The session's headline: the device where it says, the platform where not. */
fun ActiveSession.title(): String =
    deviceModel.ifBlank { platform }.ifBlank { applicationName }.ifBlank { "Unknown device" }

/**
 * The line under it: which app and where — "Telegram Desktop 5.2 · Berlin,
 * Germany". An app that is not Telegram's own says so, since a stranger's
 * client is exactly what somebody looking at this list is looking for.
 */
fun ActiveSession.appLine(): String {
    val app = listOf(applicationName, applicationVersion).filter { it.isNotBlank() }.joinToString(" ")
    val unofficial = if (!isOfficialApplication && app.isNotBlank()) " (unofficial)" else ""
    return listOf(app + unofficial, location).filter { it.isNotBlank() }.joinToString(" · ")
}

/** "Online" for this device and for one active within five minutes; the time otherwise. */
fun ActiveSession.activityLabel(nowSeconds: Long, timeLabel: (Long) -> String): String = when {
    isCurrent -> "Online"
    nowSeconds - lastActiveDate < ONLINE_WINDOW_SECONDS -> "Online"
    else -> timeLabel(lastActiveDate)
}

/** The current session first, then the others by how recently they were used. */
fun List<ActiveSession>.inDisplayOrder(): List<ActiveSession> =
    sortedWith(compareByDescending<ActiveSession> { it.isCurrent }.thenByDescending { it.lastActiveDate })

private const val ONLINE_WINDOW_SECONDS = 5 * 60
