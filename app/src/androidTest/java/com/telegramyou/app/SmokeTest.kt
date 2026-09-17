package com.telegramyou.app

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the demo client from a cold start to a conversation, on a real device.
 *
 * This is the test that would have caught "it crashes after the login code",
 * and nothing that existed before it could: the unit tests never construct a
 * screen, and a green compile says only that the code links.
 *
 * It asserts almost nothing about what things look like. What it proves is
 * that each screen appears at all — which is the failure mode this project
 * actually has, because every composable here is written blind. The
 * screenshots it leaves behind are the other half: they are the only way
 * anyone working without a device sees what was built.
 *
 * Driven through UiAutomator rather than Compose's test rule. That rule waits
 * for the composition to go idle, and this app is never idle — the typing
 * indicator and the Expressive loading indicator are infinite animations.
 * UiAutomator reads the accessibility tree, which Compose publishes anyway,
 * and does not wait for anything to stop moving.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private lateinit var device: UiDevice

    @Before
    fun launchFromCold() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()

        val context = InstrumentationRegistry.getInstrumentation().context
        val packageName = InstrumentationRegistry.getInstrumentation()
            .targetContext.packageName
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launcher activity for $packageName")
        // Cleared, so a previous test's state cannot make this one pass.
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)

        assertTrue(
            "the app never drew anything",
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), LAUNCH_TIMEOUT)
        )
    }

    @Test
    fun signsInWithTheDemoCodeAndReachesTheChatList() {
        waitFor(By.text("Your phone"), "the login screen")
        screenshot("01-login")

        type(By.text("+1 234 567 8900"), "+10000000000")
        tap(By.text("Continue"))

        waitFor(By.text("Enter code"), "the code screen")
        screenshot("02-code")

        // The field shows the demo code as its placeholder, which is also what
        // gets typed into it — so this reads oddly and is right.
        type(By.text("12345"), "12345")
        tap(By.text("Sign in"))

        // The moment that crashed on a real phone: everything above worked and
        // the chat list never arrived.
        waitFor(By.text("TelegramYou"), "the chat list")
        screenshot("03-chats")
    }

    private fun waitFor(selector: BySelector, what: String) {
        val found = device.wait(Until.hasObject(selector), STEP_TIMEOUT)
        if (!found) {
            // The shot is taken before failing, because what is on screen
            // instead is the whole question.
            screenshot("failed-waiting-for-${what.replace(' ', '-')}")
        }
        assertTrue("$what never appeared", found)
    }

    private fun tap(selector: BySelector) {
        device.wait(Until.hasObject(selector), STEP_TIMEOUT)
        device.findObject(selector)?.click() ?: error("nothing to tap: $selector")
        device.waitForIdle(IDLE_TIMEOUT)
    }

    private fun type(selector: BySelector, text: String) {
        device.wait(Until.hasObject(selector), STEP_TIMEOUT)
        val field = device.findObject(selector) ?: error("no field: $selector")
        field.click()
        field.text = text
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /**
     * Into the app's own external files, which is a path adb can pull from
     * without any permission being granted to anybody.
     */
    private fun screenshot(name: String) {
        val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        device.takeScreenshot(File(directory, "$name.png"))
    }

    private companion object {
        const val LAUNCH_TIMEOUT = 20_000L
        const val STEP_TIMEOUT = 20_000L
        const val IDLE_TIMEOUT = 5_000L
    }
}
