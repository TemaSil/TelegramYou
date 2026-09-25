package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionsStorageTest {

    private fun session(
        id: Long,
        current: Boolean = false,
        lastActive: Long = 0,
        model: String = "",
        platform: String = "",
        app: String = "Telegram Desktop",
        version: String = "5.2",
        official: Boolean = true,
        location: String = ""
    ) = ActiveSession(
        id = id, isCurrent = current, kind = DeviceKind.Windows,
        applicationName = app, applicationVersion = version, isOfficialApplication = official,
        deviceModel = model, platform = platform, systemVersion = "",
        lastActiveDate = lastActive, ipAddress = "", location = location
    )

    @Test
    fun `seventeen device types come down to nine kinds`() {
        assertEquals(DeviceKind.Android, deviceKindOf("sessionDeviceTypeAndroid"))
        assertEquals(DeviceKind.Browser, deviceKindOf("sessionDeviceTypeFirefox"))
        assertEquals(DeviceKind.Linux, deviceKindOf("sessionDeviceTypeUbuntu"))
        assertEquals(DeviceKind.Mac, deviceKindOf("sessionDeviceTypeApple"))
        assertEquals(DeviceKind.Unknown, deviceKindOf("sessionDeviceTypeSomethingNew"))
    }

    @Test
    fun `a session is named by its device, then its platform`() {
        assertEquals("Pixel 9", session(1, model = "Pixel 9", platform = "Android").title())
        assertEquals("Windows", session(1, platform = "Windows").title())
        assertEquals("Telegram Desktop", session(1).title())
    }

    @Test
    fun `the app line says where, and flags a client that is not Telegram's`() {
        assertEquals("Telegram Desktop 5.2 · Berlin, Germany", session(1, location = "Berlin, Germany").appLine())
        assertEquals("Nekogram 11.1 (unofficial)", session(1, app = "Nekogram", version = "11.1", official = false).appLine())
    }

    @Test
    fun `recent activity reads as online, older as a time`() {
        val now = 1_000_000L
        val label = { at: Long -> "at $at" }
        assertEquals("Online", session(1, current = true, lastActive = 0).activityLabel(now, label))
        assertEquals("Online", session(2, lastActive = now - 60).activityLabel(now, label))
        assertEquals("at ${now - 3600}", session(3, lastActive = now - 3600).activityLabel(now, label))
    }

    @Test
    fun `this device comes first, then the most recent`() {
        val ordered = listOf(session(1, lastActive = 10), session(2, current = true, lastActive = 5), session(3, lastActive = 20))
            .inDisplayOrder()
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }

    @Test
    fun `per-chat figures fold into one line per kind, biggest first`() {
        val slices = storageSlices(
            listOf(
                Triple("fileTypePhoto", 3_000L, 3),
                Triple("fileTypeVideo", 10_000L, 1),
                Triple("fileTypeThumbnail", 500L, 20),
                Triple("fileTypeVideoNote", 2_000L, 2),
                Triple("fileTypeSecret", 7L, 1),
                Triple("fileTypeAudio", 0L, 0)
            )
        )
        assertEquals(
            listOf(
                StorageSlice(StorageKind.Videos, 12_000, 3),
                StorageSlice(StorageKind.Photos, 3_500, 23),
                StorageSlice(StorageKind.Other, 7, 1)
            ),
            slices
        )
    }

    @Test
    fun `sizes are read in powers of a thousand`() {
        assertEquals("0 B", bytesLabel(0))
        assertEquals("999 B", bytesLabel(999))
        assertEquals("740 KB", bytesLabel(740_000))
        assertEquals("12.4 MB", bytesLabel(12_400_000))
        assertEquals("1.3 GB", bytesLabel(1_300_000_000))
    }
}
