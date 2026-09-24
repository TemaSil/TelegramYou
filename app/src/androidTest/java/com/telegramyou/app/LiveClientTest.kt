package com.telegramyou.app

import android.content.Intent
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.telegramyou.app.telegram.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The live client, as far as it can go without an account: the native
 * library loads, TDLib takes the parameters built from the real `api_id`,
 * and the app arrives at the phone-number step.
 *
 * That is the whole of what nothing else in CI has ever checked. Every other
 * test drives the demo backend, so the first time `libtdjsonjava.so` was
 * loaded or `setTdlibParameters` was sent would otherwise be on somebody's
 * phone.
 *
 * It stops there on purpose. The next step sends a login code to a real
 * phone number, and no test sends a text message to anybody.
 *
 * Runs only in a live build — the Live workflow's, whose credentials come
 * from repository secrets and whose APK never leaves the runner. In the demo
 * build it is skipped, as the demo tests are skipped in this one.
 */
@RunWith(AndroidJUnit4::class)
class LiveClientTest {

    private lateinit var device: UiDevice

    @Before
    fun onlyInALiveBuild() {
        assumeFalse("a demo build has no TDLib to start", BuildConfig.USE_DEMO_CLIENT)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun tdlibStartsAndAsksForAPhoneNumber() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("no launcher activity")
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(launch)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), LAUNCH_TIMEOUT)

        // Read from the client itself rather than from the screen: the phone
        // step's heading is also what the screen shows while TDLib is still
        // starting, and when it has failed to.
        val auth = (context.applicationContext as TelegramYouApp).telegramRepository
            .observeAuth()
        val deadline = System.currentTimeMillis() + TDLIB_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            val state = auth.value
            if (state.state == AuthState.WaitPhoneNumber && !state.isLoading) break
            if (state.state == AuthState.Error) break
            Thread.sleep(POLL)
        }
        instrumentation.uiAutomation.takeScreenshot().writeToTestStorage("live-01-phone")

        val reached = auth.value
        assertEquals(
            "TDLib did not reach the phone step; it said: ${reached.errorMessage}",
            AuthState.WaitPhoneNumber,
            reached.state
        )
        assertTrue(
            "the phone field is not on screen",
            device.wait(Until.hasObject(By.text("Your phone")), LAUNCH_TIMEOUT)
        )
    }

    private companion object {
        const val LAUNCH_TIMEOUT = 30_000L

        /**
         * A cold TDLib on an emulator: loading the library, creating its
         * database and reaching Telegram's servers from a CI runner.
         */
        const val TDLIB_TIMEOUT = 90_000L
        const val POLL = 500L
    }
}
