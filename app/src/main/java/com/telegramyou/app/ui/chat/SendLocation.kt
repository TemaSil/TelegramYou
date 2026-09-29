package com.telegramyou.app.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.LocationContent
import com.telegramyou.app.telegram.model.coordinatesLabel
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Asks for where the phone is, and sends it once found: "Send your
 * location?" with the coordinates and how close the fix is, then Send.
 *
 * No map is drawn — that needs a maps provider, a decision about somebody
 * else's service that the location cards made too (see LocationCard). The
 * permission is the caller's to have asked for; this only looks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SendLocationDialog(
    onDismiss: () -> Unit,
    onSend: (latitude: Double, longitude: Double, accuracyMeters: Double) -> Unit
) {
    val context = LocalContext.current
    var fix by remember { mutableStateOf<Location?>(null) }
    var looking by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        looking = true
        fix = currentLocation(context)
        looking = false
    }
    val found = fix
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.MyLocation, contentDescription = null) },
        title = { Text("Send your location?") },
        text = {
            when {
                looking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text("Finding you…")
                }
                found == null -> Text("Could not find where you are. Is location switched on?")
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(coordinatesLabel(LocationContent(found.latitude, found.longitude)))
                    if (found.hasAccuracy()) {
                        Text(
                            "Accurate to about ${found.accuracy.roundToInt()} m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!looking && found == null) {
                TextButton(onClick = { attempt++ }) { Text("Try again") }
            } else {
                TextButton(
                    enabled = found != null,
                    onClick = {
                        found?.let {
                            onSend(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy.toDouble() else 0.0)
                        }
                    }
                ) { Text("Send") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * One fix, fresh where the phone can give one within [FIX_TIMEOUT_MS], the
 * newest remembered one otherwise; null with location off or nothing known.
 *
 * The platform's own LocationManager, not Play services: the app does not
 * depend on Google's libraries, and "fused" is a platform provider from
 * Android 12 on. The permission check is the caller's.
 */
@SuppressLint("MissingPermission")
internal suspend fun currentLocation(context: Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val enabled = manager.getProviders(true)
    val provider = PROVIDERS.firstOrNull { it in enabled } ?: return null
    val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine<Location?> { waiting ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancel = CancellationSignal()
                waiting.invokeOnCancellation { cancel.cancel() }
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location ->
                    if (waiting.isActive) waiting.resume(location)
                }
            } else {
                // Every method spelled out: before Android 11 the interface
                // has no defaults, and a missing one is a crash when called.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (waiting.isActive) waiting.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {}

                    @Deprecated("Called only before Android 10")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                waiting.invokeOnCancellation { manager.removeUpdates(listener) }
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        }
    }
    return fresh ?: enabled.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
}

/** Best first: the platform's fused provider, then satellites, then the network. */
private val PROVIDERS = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

/** How long to wait for a fresh fix before settling for the last one known. */
private const val FIX_TIMEOUT_MS = 15_000L

/** Asked for together; Android lets the person grant only the coarse one. */
internal val LOCATION_PERMISSIONS = arrayOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION
)

internal fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}
