package com.telegramyou.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun v(text: String) = AppVersion.find(text)!!

    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(v("0.2.300") > v("0.2.294"))
        assertTrue(v("0.2.1000") > v("0.2.999"))
        assertTrue(v("0.3.1") > v("0.2.999"))
        assertEquals(0, v("0.2.5").compareTo(v("0.2.5.0")))
    }

    @Test
    fun `a release's version is found in its title`() {
        assertEquals("0.2.294", AppVersion.find("TelegramYou 0.2.294").toString())
        assertNull(AppVersion.find("TelegramYou"))
        assertNull(AppVersion.find(""))
    }

    private val assets = mapOf(
        APK_ASSET to ("https://github.com/o/r/releases/download/latest/$APK_ASSET" to 64_670_414L)
    )

    @Test
    fun `a release is read from its title, body and asset`() {
        val sha = "bff6e05e2792f654c355cb53cd414535aeca72e5"
        val release = releaseOf("TelegramYou 0.2.300", "Version `0.2.300`, built from\n$sha.", assets)!!
        assertEquals(v("0.2.300"), release.version)
        assertEquals(64_670_414L, release.size)
        assertEquals(sha, release.commit)
        assertTrue(isUpdate(release, v("0.2.294")))
        assertFalse("the same build is not an update", isUpdate(release, v("0.2.300")))
        assertFalse("nor is an older one", isUpdate(release, v("0.2.301")))
    }

    @Test
    fun `no APK or no version is no release`() {
        assertNull(releaseOf("TelegramYou 0.2.300", "", emptyMap()))
        assertNull(releaseOf("Latest", "", assets))
    }

    @Test
    fun `sizes read as a person would say them`() {
        assertEquals("64.7 MB", sizeLabel(64_670_414))
        assertEquals("12 KB", sizeLabel(11_200))
        assertEquals("", sizeLabel(0))
    }
}
