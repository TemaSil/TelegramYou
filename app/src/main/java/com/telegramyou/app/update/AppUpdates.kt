package com.telegramyou.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.telegramyou.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where checking for, fetching and installing a newer build has got to. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    /** [progress] is 0..1, or null while the size is not known. */
    data class Downloading(val release: Release, val progress: Float?) : UpdateState
    data class Ready(val release: Release, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates without the Play Store: this app is published as one APK on the
 * repository's `latest` release, and this is how it finds a newer one,
 * fetches it and hands it to Android's installer.
 *
 * Every build is signed by the same tracked debug key (see CLAUDE.md), which
 * is what lets a newer APK install over the one running — the installer
 * refuses one signed by anything else.
 *
 * The release API is asked without a token: sixty requests an hour from one
 * address is more than a person pressing a button will ever use, and a token
 * in the APK would be a secret anyone could read.
 *
 * One per process, held by `TelegramYouApp`, so the state survives the
 * settings screen being left and reopened while a download runs.
 */
class AppUpdates(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** This build's version, as its APK and the release name write it. */
    val installed: AppVersion = AppVersion.find(BuildConfig.VERSION_NAME) ?: AppVersion(listOf(0))

    private var work: Job? = null

    /**
     * Asks the release page for the newest build. [quiet] is the check made
     * at start-up: it says nothing unless there is something newer, so a
     * phone offline at launch does not come up with an error for it.
     */
    fun check(quiet: Boolean = false) {
        if (work?.isActive == true) return
        work = scope.launch {
            if (!quiet) _state.value = UpdateState.Checking
            val result = try {
                withContext(Dispatchers.IO) { fetchRelease() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "update check: ${e.message}")
                if (!quiet) _state.value = UpdateState.Failed("Could not reach GitHub")
                return@launch
            }
            _state.value = when {
                result != null && isUpdate(result, installed) -> UpdateState.Available(result)
                quiet -> _state.value
                else -> UpdateState.UpToDate
            }
        }
    }

    /** Fetches the APK the available release points at, reporting how far it has got. */
    fun download() {
        val release = (_state.value as? UpdateState.Available)?.release
            ?: (_state.value as? UpdateState.Failed)?.let { lastRelease }
            ?: return
        if (work?.isActive == true) return
        lastRelease = release
        work = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            try {
                val apk = withContext(Dispatchers.IO) { fetchApk(release) }
                _state.value = UpdateState.Ready(release, apk)
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "update download: ${e.message}")
                _state.value = UpdateState.Failed("The download stopped. Try again.")
            }
        }
    }

    private var lastRelease: Release? = null

    /**
     * Hands the downloaded APK to Android's installer, or first sends the
     * person to the one setting that allows it: an app may only install
     * others once it has been allowed to, and Android asks for that in
     * Settings, not in a dialog.
     */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.apk)
        launch(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun launch(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("Nothing on this phone can install the update")
        }
    }

    private fun fetchRelease(): Release? {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub answered ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            val byName = buildMap {
                for (index in 0 until (assets?.length() ?: 0)) {
                    val asset = assets?.optJSONObject(index) ?: continue
                    put(
                        asset.optString("name"),
                        asset.optString("browser_download_url") to asset.optLong("size")
                    )
                }
            }
            return releaseOf(json.optString("name"), json.optString("body"), byName)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Into this app's cache, under the one folder its file provider shares —
     * the installer reads it from there. The previous download is replaced:
     * there is only ever one update worth keeping.
     */
    private fun fetchApk(release: Release): File {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "TelegramYou-${release.version}.apk")
        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub answered ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            var done = 0L
            var reported = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        // A state per percent, not per buffer: a 60 MB file is
                        // a thousand buffers, and each state is a recomposition.
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != reported) {
                            reported = percent
                            _state.value = UpdateState.Downloading(
                                release,
                                if (total > 0) done.toFloat() / total else null
                            )
                        }
                    }
                }
            }
            if (total > 0 && done != total) error("Got $done of $total bytes")
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "AppUpdates"
        const val RELEASE_API = "https://api.github.com/repos/TemaSil/TelegramYou/releases/tags/latest"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val TIMEOUT_MILLIS = 20_000
    }
}
