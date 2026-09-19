package com.telegramyou.app.ui.chat

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalMultiBrowseCarousel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.telegramyou.app.ui.media.PhotoAccess
import com.telegramyou.app.ui.media.RECENT_PHOTO_COUNT
import com.telegramyou.app.ui.media.photoAccessOf
import com.telegramyou.app.ui.media.photoPermissions
import com.telegramyou.app.ui.media.shouldOfferMorePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The strip of recent pictures across the top of the attachment sheet, and
 * the media-library read behind it.
 *
 * Kept out of ChatScreen.kt because it is the only thing in the app that
 * touches MediaStore, and because the permission dance around it is longer
 * than the sheet it decorates.
 */

/** Tall enough for the mask to read as a picture rather than a stripe. */
private val CarouselHeight = 176.dp

/**
 * Asked at most once per process.
 *
 * Remembering it in the composition would mean re-asking on every open of the
 * sheet, because the sheet is thrown away when it closes. Android silently
 * drops a request that was already refused twice, but the second dialog is
 * one the user would still see — and would have asked for by doing nothing
 * but tapping the paperclip again.
 */
private var requestedThisProcess = false

/** What this app may currently read, as the platform answers it right now. */
private fun photoAccess(context: Context): PhotoAccess = photoAccessOf(
    Build.VERSION.SDK_INT,
    photoPermissions(Build.VERSION.SDK_INT).associateWith { permission ->
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
)

/**
 * The newest pictures on the device, newest first, as content Uris.
 *
 * Under Android 14's partial access the same query returns only what the user
 * selected, which is exactly the right behaviour and needs no branch here.
 */
private suspend fun recentPhotos(
    context: Context,
    limit: Int = RECENT_PHOTO_COUNT
): List<String> = withContext(Dispatchers.IO) {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    try {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            // No LIMIT in the sort order: MediaStore stopped honouring SQL
            // appended to it, and the cursor is walked lazily anyway, so
            // stopping after `limit` rows reads no more than `limit` rows.
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val found = ArrayList<String>(limit)
            while (found.size < limit && cursor.moveToNext()) {
                found += ContentUris.withAppendedId(collection, cursor.getLong(id)).toString()
            }
            found
        } ?: emptyList()
    } catch (denied: SecurityException) {
        // A grant can be withdrawn while the app is alive, and the sheet
        // still has three working rows underneath this one.
        emptyList()
    }
}

/**
 * The recent pictures, as Material's carousel.
 *
 * `HorizontalMultiBrowseCarousel` is what Material ships for a strip of
 * images whose edges compress — the shape this wants — so there is nothing to
 * draw by hand. It renders nothing at all when there is no access or no
 * picture to show, which is what keeps the sheet working when the permission
 * is refused.
 *
 * Tapping a picture attaches it and closes the sheet: it is one tap for the
 * thing that was already on screen, and the gallery row below is still there
 * for choosing several.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentPhotoCarousel(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(photoAccess(context)) }
    var photos by remember { mutableStateOf(emptyList<String>()) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        access = photoAccessOf(Build.VERSION.SDK_INT, granted)
    }

    // Asked on opening the sheet rather than at startup: this is the moment
    // the pictures are actually wanted, which is the only moment the question
    // makes sense.
    LaunchedEffect(Unit) {
        if (access == PhotoAccess.None && !requestedThisProcess) {
            requestedThisProcess = true
            request.launch(photoPermissions(Build.VERSION.SDK_INT).toTypedArray())
        }
    }

    LaunchedEffect(access) {
        photos = if (access == PhotoAccess.None) emptyList() else recentPhotos(context)
    }

    if (photos.isEmpty()) return

    Column(modifier) {
        val state = rememberCarouselState { photos.size }
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 168.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(CarouselHeight)
        ) { index ->
            val uri = photos[index]
            AsyncImage(
                model = uri,
                contentDescription = "Recent photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(CarouselHeight)
                    // Clipped before the click so the ripple follows the
                    // carousel's mask as it compresses at the edges.
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .clickable { onPick(uri) }
            )
        }

        if (shouldOfferMorePhotos(access)) {
            // Only under partial access, where the strip is genuinely
            // incomplete and the platform will show the picker again.
            TextButton(
                onClick = {
                    request.launch(photoPermissions(Build.VERSION.SDK_INT).toTypedArray())
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Select more photos")
            }
        }
    }
}
