package com.telegramyou.app

import android.content.Intent
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        launchApp()
    }

    /**
     * Brings the app up from the launcher, clearing whatever was on the stack.
     *
     * A method rather than only a `@Before` because the reply test leaves the
     * app to type into the shade and has to come back afterwards: whether the
     * message actually arrived is a question only the conversation can answer.
     */
    private fun launchApp() {
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
        signIn()

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

        // And out again, to watch a notification arrive. The demo backend has
        // a chat that speaks on a timer precisely so this can be checked with
        // no account and no live server.
        //
        // Leaving the app is not incidental. The chat that speaks is the one
        // just opened, so while it is on screen its messages are suppressed —
        // which is the behaviour, not an obstacle. Only once the activity has
        // stopped is there anything to see.
        device.pressHome()
        device.waitForIdle(IDLE_TIMEOUT)
        waitForNotification()
        screenshot("05-notification")
    }

    /**
     * A group's header, which is the only place the avatar cluster appears.
     *
     * A second test rather than more of the first: that one ends on the home
     * screen with the shade open, and getting from there to another
     * conversation means starting over — which is what a fresh test method
     * does for free.
     *
     * The chat it opens is the seeded group with several people talking in
     * it. A cluster needs more than one member; with one it falls back to a
     * single avatar, correctly and without showing anything.
     */
    @Test
    fun aGroupHeaderShowsItsMembers() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")

        tap(By.text(GROUP_CHAT))
        // Anchored on a message only this group has, so landing in the wrong
        // conversation fails here rather than producing a confident
        // screenshot of the wrong screen. It has to be a recent one: the
        // conversation opens at the bottom, and the group's oldest message —
        // the first thing anchored on here — was scrolled off the top.
        waitFor(By.textContains("Figma dump"), "the group")

        // The demo chat speaks on a timer, and its heads-up notification
        // lands across the top of the screen — over the very header this
        // test exists to photograph. Waiting for it to go is cheap: it names
        // the chat it came from, which is not this one, so its absence is
        // exactly the condition wanted.
        device.wait(Until.gone(By.text(NOTIFYING_CHAT)), HEADS_UP_TIMEOUT)
        screenshot("06-group-header")
    }

    /**
     * Replying from the shade, watched rather than reasoned about.
     *
     * This is the oldest debt in ROADMAP.md. The reply path — a `RemoteInput`
     * into a receiver that sends with `goAsync` and takes the notification
     * down only once the message has gone — was written, compiled and ticked
     * without anything ever typing into it. The emulator saw the notification
     * arrive and stopped there.
     *
     * What makes this a real test rather than a screenshot is the last step:
     * it comes back into the app and looks for the text in the conversation.
     * A reply that opened a field, accepted characters and sent nothing would
     * pass every earlier assertion here.
     */
    @Test
    fun repliesFromTheNotificationShade() {
        signIn()
        waitFor(By.text(NOTIFYING_CHAT), "the chat list")

        // Out of the app, so the chat that speaks on a timer is not on screen
        // and its messages are not suppressed.
        device.pressHome()
        device.waitForIdle(IDLE_TIMEOUT)
        waitForNotification()

        openReplyField()
        typeReply(REPLY_TEXT)
        screenshot("07-notification-reply")
        sendReply()

        device.pressBack()
        launchApp()
        waitFor(By.text(NOTIFYING_CHAT), "the chat list")
        tap(By.text(NOTIFYING_CHAT))
        // The whole point, and the only assertion that can prove it. The
        // notification going away would not: the demo chat speaks every
        // twenty-five seconds, so one from the same chat is along shortly
        // whether or not anything was sent — which is what the first version
        // of this test asserted, and why it failed on a working reply path.
        waitFor(By.textContains(REPLY_TEXT), "the reply in the conversation")
        screenshot("08-reply-arrived")
    }

    /**
     * Puts [text] into the reply field, and proves it landed there.
     *
     * Not [type]: that takes the first `android.widget.EditText` on screen,
     * and the shade has more than one — the first run of this typed into
     * something else entirely and left the reply field showing its
     * placeholder, which the screenshot showed and no assertion caught. The
     * failure only surfaced two steps later as "the reply was never sent",
     * which is true and unhelpful.
     *
     * So the field is found by what it is — systemui's own id, then whatever
     * has focus, then an EditText — and then read back. A field that accepted
     * nothing fails here, naming itself.
     */
    private fun typeReply(text: String) {
        val field = device.findObject(By.res(SYSTEM_UI, "remote_input_text"))
            ?: device.findObject(By.focused(true).clazz("android.widget.EditText"))
            ?: device.findObject(By.clazz("android.widget.EditText"))
            ?: run {
                screenshot("failed-finding-the-reply-field")
                fail("the notification's reply field was not on screen")
                return
            }
        field.click()
        field.text = text
        device.waitForIdle(IDLE_TIMEOUT)
        assertEquals("the reply field did not take the text", text, field.text)
    }

    /**
     * Sends what was typed.
     *
     * The arrow by its id where systemui offers one, and Enter otherwise. Not
     * Enter alone: whether the key sends or inserts a newline depends on the
     * keyboard, and the arrow is what a person would press.
     */
    private fun sendReply() {
        val send = device.findObject(By.res(SYSTEM_UI, "remote_input_send"))
        if (send != null) send.click() else device.pressEnter()
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /**
     * Opens the notification's reply field, expanding it first if it is shut.
     *
     * A collapsed notification shows no actions at all, and whether it arrives
     * collapsed depends on the version, the shade's state and how many others
     * are up — so the expander is tried rather than assumed, and the action is
     * matched case-insensitively for the reason [allowNotifications] gives
     * about platform strings.
     */
    private fun openReplyField() {
        val reply = By.text(Pattern.compile("reply", Pattern.CASE_INSENSITIVE))
        if (!device.wait(Until.hasObject(reply), DIALOG_TIMEOUT)) {
            device.findObject(By.res("android:id/expand_button"))?.click()
                ?: device.findObject(By.textContains(NOTIFYING_CHAT))?.click()
            device.waitForIdle(IDLE_TIMEOUT)
        }
        if (!device.wait(Until.hasObject(reply), STEP_TIMEOUT)) {
            screenshot("failed-finding-the-reply-action")
            fail("the notification had no Reply action")
        }
        device.findObject(reply)?.click()
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /**
     * Signs in, if signing in is what the app is asking for.
     *
     * The second test to run finds itself already signed in, and that is not
     * a leak to be plugged: `FLAG_ACTIVITY_CLEAR_TASK` in `launchFromCold`
     * clears the activity stack, not the process, and the demo client holds
     * its authentication in `Application`, which outlives both tests. The
     * first version of this waited for the login screen unconditionally and
     * failed with "the login screen never appeared" — a true statement about
     * a working app.
     *
     * So the login screen is looked for rather than expected. Whichever test
     * runs first takes the screenshots of it; JUnit does not promise an
     * order, and it does not matter which one does.
     */
    private fun signIn() {
        val loginIsUp = device.wait(
            Until.hasObject(By.text("Your phone")),
            DIALOG_TIMEOUT
        )
        if (!loginIsUp) return
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
        // the system dialog covers the very chat the tests wait for. Granting
        // it rather than dismissing it, because a refusal would leave the
        // notification service posting into nothing for the rest of the run.
        allowNotifications()
    }

    /**
     * Opens the shade and waits for the demo chat to appear in it.
     *
     * Reopened on each attempt rather than opened once: the shade can be
     * closed by the system between polls, and a wait against a shade that is
     * no longer up would time out on a notification that did arrive.
     *
     * The timeout has to exceed the demo chat's interval twice over. The
     * first message it sends lands while the conversation is still on screen
     * and is suppressed, so the one this waits for is the second.
     */
    private fun waitForNotification() {
        val entry = By.textContains(NOTIFYING_CHAT)
        val deadline = System.currentTimeMillis() + NOTIFICATION_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            device.openNotification()
            if (device.wait(Until.hasObject(entry), SHADE_POLL)) return
        }
        screenshot("failed-waiting-for-the-notification")
        fail("no notification from $NOTIFYING_CHAT ever arrived")
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

        /**
         * The chat the demo backend's timer writes to — the first unmuted one
         * it finds, which is the seeded "Material Design".
         */
        const val NOTIFYING_CHAT = "Material Design"

        /** systemui, which owns the shade and the reply field in it. */
        const val SYSTEM_UI = "com.android.systemui"

        /** Distinctive enough that finding it cannot be a coincidence. */
        const val REPLY_TEXT = "Replied from the shade"

        /** The seeded group with more than one person talking in it. */
        const val GROUP_CHAT = "Design Circle"

        /**
         * Long enough for a heads-up notification to retreat into the status
         * bar, which Android does after a few seconds by itself.
         */
        const val HEADS_UP_TIMEOUT = 10_000L

        /**
         * Longer than twice the demo chat's twenty-five second interval,
         * because the first message it sends arrives while the conversation
         * is still open and is deliberately suppressed.
         */
        const val NOTIFICATION_TIMEOUT = 70_000L

        /** One look at the shade before reopening it and looking again. */
        const val SHADE_POLL = 5_000L
    }
}
