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
        // 1.0 took over from 0.2 with the run number still counting on, so a
        // phone on the last 0.2 is offered the first 1.0.
        assertTrue(v("1.0.310") > v("0.2.309"))
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
    fun `from 1_1 the build is named beside the version and decides`() {
        val release = releaseOf("TelegramYou 1.1 · build 430", "", assets)!!
        assertEquals(v("1.1"), release.version)
        assertEquals(430, release.build)
        assertEquals("1.1 (build 430)", release.label)
        assertTrue("a newer build of the same version", isUpdate(release, v("1.1"), 429))
        assertFalse("the same build is not an update", isUpdate(release, v("1.1"), 430))
        assertFalse("nor is an older one", isUpdate(release, v("1.1"), 431))
    }

    @Test
    fun `a phone on the last 1_0 build is offered the first 1_1`() {
        // Code from before 1.1 reads only the version out of the title...
        val release = releaseOf("TelegramYou 1.1 · build 430", "", assets)!!
        assertTrue(isUpdate(release.copy(build = null), v("1.0.429")))
        // ...and the first dotted run in it is the 1.1, not the build.
        assertEquals("1.1", AppVersion.find("TelegramYou 1.1 · build 430").toString())
        assertTrue(v("1.1") > v("1.0.429"))
        assertTrue(v("1.1.5") > v("1.1") && v("1.2") > v("1.1.5"))
    }

    @Test
    fun `a release named before 1_1 has no build`() {
        assertNull(releaseOf("TelegramYou 1.0.423", "", assets)!!.build)
        assertEquals("1.0.423", releaseOf("TelegramYou 1.0.423", "", assets)!!.label)
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
