package com.telegramyou.app.ui.media

/**
 * Which of the device's photos this app may read.
 *
 * Android 14 split the answer in three: the whole library, the handful of
 * pictures the user picked in the system dialog, or nothing. The middle case
 * is not a degraded version of the first — it is a deliberate answer, and the
 * app that treats it as a refusal is the reason people stop granting anything.
 *
 * Pure so that the branching can be tested; the strings are permission names
 * rather than `Manifest.permission` constants because this module does not
 * see the Android SDK. They are duplicated here on purpose, with the manifest
 * as the other copy.
 */
enum class PhotoAccess {
    /** Every picture on the device. */
    Full,

    /** Only what the user picked in the Android 14 partial-access dialog. */
    Partial,

    /** Nothing, either because the dialog was refused or never shown. */
    None
}

const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED =
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
const val PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

/** Android 13, where READ_EXTERNAL_STORAGE stopped covering images. */
const val SDK_TIRAMISU = 33

/** Android 14, which added partial access to the visual media permissions. */
const val SDK_UPSIDE_DOWN_CAKE = 34

/**
 * What to ask for on this platform version.
 *
 * On Android 14 and later both are requested together: asking for
 * READ_MEDIA_IMAGES alone still shows the photo-picker-shaped dialog, but
 * without the "Select photos" choice, so the user is offered all or nothing
 * where the platform offers three answers.
 */
fun photoPermissions(sdk: Int): List<String> = when {
    sdk >= SDK_UPSIDE_DOWN_CAKE ->
        listOf(PERMISSION_READ_MEDIA_IMAGES, PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED)
    sdk >= SDK_TIRAMISU -> listOf(PERMISSION_READ_MEDIA_IMAGES)
    else -> listOf(PERMISSION_READ_EXTERNAL_STORAGE)
}

/**
 * Reads the grant map the request came back with — or the one assembled by
 * checking the same permissions before asking, which is the same shape.
 */
fun photoAccessOf(sdk: Int, granted: Map<String, Boolean>): PhotoAccess = when {
    granted[PERMISSION_READ_MEDIA_IMAGES] == true -> PhotoAccess.Full
    granted[PERMISSION_READ_EXTERNAL_STORAGE] == true && sdk < SDK_TIRAMISU ->
        PhotoAccess.Full
    // Only Android 14 can answer this way, and only it can act on the offer
    // to widen the selection afterwards.
    sdk >= SDK_UPSIDE_DOWN_CAKE &&
        granted[PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED] == true -> PhotoAccess.Partial
    else -> PhotoAccess.None
}

/**
 * Whether the sheet should offer to widen the selection.
 *
 * Only under partial access: with the whole library there is nothing to add,
 * and with none the offer would stand in for the request itself, which is a
 * different button in a different place.
 */
fun shouldOfferMorePhotos(access: PhotoAccess): Boolean = access == PhotoAccess.Partial

/**
 * How many recent pictures the carousel is worth reading.
 *
 * The carousel is the top of a sheet, not a gallery: three or four are on
 * screen and a flick reaches the rest. Reading the whole library to show that
 * costs a cursor over thousands of rows for pictures nobody scrolls to — the
 * gallery row underneath is what opens the library.
 */
const val RECENT_PHOTO_COUNT = 24
