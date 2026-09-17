package com.telegramyou.app

import android.content.Intent
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.regex.Pattern
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

        // Into the field, not into its placeholder. Compose publishes a
        // TextField as an EditText in the accessibility tree and the
        // placeholder as a plain TextView beside it — typing into the latter
        // does nothing, silently, which is how the first run of this test
        // "failed to reach the code screen" while the app was working fine.
        type("+10000000000")
        tap(By.text("Continue"))

        waitFor(By.text("Enter code"), "the code screen")
        screenshot("02-code")

        type("12345")
        tap(By.text("Sign in"))

        // The chat list asks for POST_NOTIFICATIONS the moment it appears, and
        // the system dialog covers the very chat this test waits for. Granting
        // it rather than dismissing it, because a refusal would leave the
        // notification service posting into nothing for the rest of the run.
        allowNotifications()

        // Anchored on a chat the demo backend seeds, not on the app's name.
        // "TelegramYou" is written across the login screen too, so waiting for
        // it passed while the app was crashing on the way to the chat list —
        // a green test and a screenshot of the code screen labelled "chats".
        waitFor(By.text("Material Design"), "the chat list")
        screenshot("03-chats")

        // Into a conversation, which is where most of this project's code is.
        tap(By.text("Material Design"))
        waitFor(By.text("Welcome to TelegramYou"), "the conversation")
        screenshot("04-chat")
    }

    /**
     * Answers the notification permission dialog, if one is up.
     *
     * Not a [waitFor]: below Android 13 the permission is granted at install
     * and no dialog appears, so its absence is not a failure. The wait is
     * short for the same reason — this is a look, not an expectation.
     *
     * The button's label is the system's, not ours, so it is matched
     * case-insensitively: it has been "Allow" and "ALLOW" on different
     * versions, and a test that breaks on the capitalisation of a platform
     * string tells nobody anything about this app.
     */
    private fun allowNotifications() {
        val allow = By.text(Pattern.compile("allow", Pattern.CASE_INSENSITIVE))
        if (device.wait(Until.hasObject(allow), DIALOG_TIMEOUT)) {
            device.findObject(allow)?.click()
            device.waitForIdle(IDLE_TIMEOUT)
        }
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

    /**
     * Types into the one text field on screen.
     *
     * By class rather than by the text in it: the placeholder is a separate
     * node that happens to carry the words, and setting text on that is a
     * no-op that reports success.
     */
    private fun type(text: String) {
        val field = By.clazz("android.widget.EditText")
        device.wait(Until.hasObject(field), STEP_TIMEOUT)
        val node = device.findObject(field) ?: error("no text field on screen")
        node.click()
        node.text = text
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /**
     * Through TestStorage, so the file outlives the app.
     *
     * The obvious place — the app's own external files — is deleted when
     * Gradle uninstalls the APK at the end of the run, which happens before
     * anything can copy it off the device. TestStorage is served by a separate
     * APK that is still there afterwards, and AGP collects what it holds into
     * build/outputs.
     */
    private fun screenshot(name: String) {
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        shot.writeToTestStorage(name)
    }

    private companion object {
        const val LAUNCH_TIMEOUT = 20_000L
        const val STEP_TIMEOUT = 20_000L
        const val IDLE_TIMEOUT = 5_000L

        /**
         * Short, because a missing permission dialog is a valid outcome —
         * below Android 13 there is none — and this would otherwise add
         * twenty seconds of waiting for nothing to every run.
         */
        const val DIALOG_TIMEOUT = 4_000L
    }
}
