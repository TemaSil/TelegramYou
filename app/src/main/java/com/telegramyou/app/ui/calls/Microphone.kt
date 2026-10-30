package com.telegramyou.app.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * [action] once the microphone is granted (2.9): straight away when it
 * already is, after Android's own question when it is not, and not at all
 * when the answer is no — a call nobody can speak into is not started.
 */
@Composable
fun rememberWithMicrophone(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(action)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latest()
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) latest() else ask.launch(Manifest.permission.RECORD_AUDIO)
    }
}
