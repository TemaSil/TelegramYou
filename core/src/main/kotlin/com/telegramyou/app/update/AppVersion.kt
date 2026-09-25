package com.telegramyou.app.update

/**
 * A version as this project writes them: "0.2.294", the last number being
 * the build — CI's run number, so every published APK has a larger one than
 * the one before.
 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 } - other.parts.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /**
         * The first dotted version inside [text] — a release is named
         * "TelegramYou 0.2.294", and the number is what matters. Null when
         * there is none.
         */
        fun find(text: String): AppVersion? =
            Regex("""\d+(\.\d+)+""").find(text)?.value
                ?.split('.')
                ?.map { it.toIntOrNull() ?: return null }
                ?.let(::AppVersion)
    }
}

/** The newest published build, as the release page describes it. */
data class Release(
    val version: AppVersion,
    val downloadUrl: String,
    /** In bytes; 0 when the page did not say. */
    val size: Long,
    /** The commit it was built from, when the release says. */
    val commit: String? = null
)

/**
 * The APK and version out of the release API's answer, already reduced to
 * the fields that matter: the release's [name], and its assets as name to
 * (url, size). [assetName] is the one file CI publishes.
 */
fun releaseOf(
    name: String,
    body: String,
    assets: Map<String, Pair<String, Long>>,
    assetName: String = APK_ASSET
): Release? {
    val version = AppVersion.find(name) ?: return null
    val (url, size) = assets[assetName] ?: return null
    val commit = Regex("""\b[0-9a-f]{40}\b""").find(body)?.value
    return Release(version, url, size, commit)
}

/** Whether [release] is worth offering to an app at [installed]. */
fun isUpdate(release: Release, installed: AppVersion): Boolean = release.version > installed

/** "64.7 MB" — how big the download is, for the button that starts it. */
fun sizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1_000_000 -> "${(bytes + 999) / 1000} KB"
    else -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0)
}

/** The file every green build of main is published as. */
const val APK_ASSET = "TelegramYou-debug.apk"
