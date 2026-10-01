package com.telegramyou.app.update

/**
 * A version as this project writes them: "1.1", "1.1.5", "1.2" — set by hand
 * in `app/build.gradle.kts` when the owner says the app has moved on.
 *
 * Until 1.1 the last part was the build, "1.0.294"; those still parse and
 * still compare below every 1.1, which is what lets a phone on the last
 * 1.0 build be offered the first 1.1. The build now travels on its own —
 * see [Release.build].
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
         * "TelegramYou 1.1 · build 430", and the number is what matters.
         * Null when there is none.
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
    /**
     * CI's run number, which is also the APK's versionCode: every build of
     * main has a larger one, while [version] stays at 1.1 until the owner
     * moves it. Null on releases named before 1.1.
     */
    val build: Int? = null,
    val downloadUrl: String,
    /** In bytes; 0 when the page did not say. */
    val size: Long,
    /** The commit it was built from, when the release says. */
    val commit: String? = null,
    /** What it brings, when the release carries notes; see [WhatsNew]. */
    val notes: WhatsNew? = null,
    /** When it went up, in epoch seconds; see [releasedLabel]. */
    val publishedSeconds: Long? = null
) {
    /** "1.1 (build 430)" — two builds of one version need telling apart. */
    val label: String get() = versionLabel(version, build)
}

/** How a version is written where two builds of it could be confused. */
fun versionLabel(version: AppVersion, build: Int?): String =
    if (build == null) version.toString() else "$version (build $build)"

/**
 * The APK and version out of the release API's answer, already reduced to
 * the fields that matter: the release's [name], and its assets as name to
 * (url, size). [assetName] is the one file CI publishes.
 */
fun releaseOf(
    name: String,
    body: String,
    assets: Map<String, Pair<String, Long>>,
    assetName: String = APK_ASSET,
    publishedAt: String? = null
): Release? {
    val version = AppVersion.find(name) ?: return null
    val build = Regex("""\bbuild (\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
    val (url, size) = assets[assetName] ?: return null
    val commit = Regex("""\b[0-9a-f]{40}\b""").find(body)?.value
    return Release(version, build, url, size, commit, whatsNewIn(body), publishedSecondsOf(publishedAt))
}

/**
 * Whether [release] is worth offering to an app at [installed], built as
 * [installedBuild]. The build decides when the release names one: every
 * push to main is a new build of the same 1.1, and a phone should still be
 * offered it. A release named before 1.1 has only its version to go by.
 */
fun isUpdate(release: Release, installed: AppVersion, installedBuild: Int? = null): Boolean =
    if (release.build != null && installedBuild != null) {
        release.build > installedBuild
    } else {
        release.version > installed
    }

/** "64.7 MB" — how big the download is, for the button that starts it. */
fun sizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1_000_000 -> "${(bytes + 999) / 1000} KB"
    else -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0)
}

/** The file every green build of main is published as. */
const val APK_ASSET = "TelegramYou-debug.apk"
