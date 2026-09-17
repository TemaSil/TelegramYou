package com.telegramyou.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for POST_NOTIFICATIONS once, the first time the chat list is shown.
 *
 * On Android 13 and up this permission is the whole feature: without it the
 * service posts into nothing and a person who installed a messenger never
 * hears from it. A service cannot ask — only something with a screen in front
 * of someone can — so the request lives here rather than next to the code
 * that needs it.
 *
 * Asked from the chat list rather than at launch, and that is deliberate: a
 * permission dialog thrown at the login screen asks for something the app has
 * not yet shown a reason for, and the usual answer to that is no. By the time
 * the chat list is up, "let us tell you when these people write" is a
 * question that explains itself.
 *
 * Asked once and not pressed again. Android stops showing the dialog after a
 * refusal anyway, and a client that nags is worse than one that is quiet:
 * anyone who changes their mind can grant it in system settings.
 */
@Composable
fun RequestNotificationPermission() {
    // The permission does not exist below 33, and requesting it there returns
    // a refusal for a permission that was granted at install time.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Either answer is final; the service checks before it posts. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
