package com.telegramyou.app

import android.app.NotificationManager
import android.content.ContentValues
import androidx.core.app.NotificationCompat
import com.telegramyou.app.notifications.PostedNotifications
import android.os.SystemClock
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.After
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
        // Every test here drives the demo backend — the code 12345, the
        // seeded chats, the chat that speaks on a timer. A live build has
        // none of those, and LiveClientTest is its test instead.
        assumeTrue("the smoke test drives the demo client", BuildConfig.USE_DEMO_CLIENT)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Heads-up notifications off for the run, and this is the fix rather
        // than a convenience. The demo chat speaks every twenty-five seconds
        // and its heads-up lands across the app bar; UiAutomator finds the
        // control underneath it, clicks where it is, and the tap goes to the
        // notification instead — which opened the wrong chat in three
        // different tests on three different days, each time fixed locally by
        // waiting the notification out, and each time it came back somewhere
        // else. Nothing is lost: the reply test opens the shade itself, and
        // what it looks for is in there either way.
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        device.pressHome()
        launchApp()
    }

    /** Left as it was found, for whatever runs on this emulator next. */
    @After
    fun restoreHeadsUpNotifications() {
        // Skipped in a live build before the device was ever set up, and
        // JUnit runs this regardless.
        if (!::device.isInitialized) return
        device.executeShellCommand("settings put global heads_up_notifications_enabled 1")
        // A test that turned the screen leaves it upright and free again.
        device.setOrientationNatural()
        device.unfreezeRotation()
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
     * The rail, which only exists where the window is big enough for one.
     *
     * Not by turning the phone, which was the first version of this and
     * failed for a reason worth keeping: Material leaves the bar along the
     * bottom whenever the window is short, and a phone in landscape is
     * short. That is its rule, not a bug, so a rail cannot appear there.
     *
     * So the window itself is resized to a tablet's — wide *and* tall — for
     * the length of this test, and put back afterwards. What is asserted is
     * where the destinations end up: down the left-hand side rather than
     * across the bottom, which is the one thing a bar masquerading as a rail
     * would fail.
     */
    @Test
    fun aTabletSizedWindowGetsARail() {
        // Signed in first, at the size the login screen was laid out for.
        // Resizing recreates the activity, and doing it before logging in
        // left this test tapping for a Continue button that had gone: the
        // demo client keeps its authentication in Application, so the app
        // comes back up at tablet size on the chat list rather than the
        // login screen.
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")

        try {
            // A tablet, in the only terms the emulator takes: 2000x1400 at
            // 240dpi is 1333x933dp, which is expanded in width and medium in
            // height — the case Material answers with a rail.
            device.executeShellCommand("wm size 2000x1400")
            device.executeShellCommand("wm density 240")
            device.waitForIdle(IDLE_TIMEOUT)

            waitFor(By.text("Chats"), "the navigation")
            val chats = device.findObject(By.text("Chats"))
                ?: run {
                    screenshot("failed-finding-the-rail")
                    fail("the navigation had no Chats destination")
                    return
                }
            screenshot("13-rail")
            assertTrue(
                "the destinations should be down the side, not across the bottom",
                chats.visibleBounds.centerX() < device.displayWidth / 4
            )
        } finally {
            // Whatever happened, the next test gets the phone it expects —
            // and gets it settled. Resetting the size tears every window
            // down and builds it again, and the test that ran next found
            // nothing on screen and failed in its own @Before: waiting for
            // idle is not enough, so this waits for the launcher to come
            // back the way launchApp waits for the app.
            device.executeShellCommand("wm density reset")
            device.executeShellCommand("wm size reset")
            device.pressHome()
            device.wait(
                Until.hasObject(By.pkg(device.launcherPackageName).depth(0)),
                LAUNCH_TIMEOUT
            )
            device.waitForIdle(IDLE_TIMEOUT)
        }
    }

    /**
     * A video message, opened and played.
     *
     * The demo build ships a four-second clip precisely so this can be
     * checked with no account: the bubble draws its poster, tapping it opens
     * the player, and the player's own scrubber proves the thing is running
     * rather than merely on screen — a still first frame would satisfy every
     * weaker assertion.
     */
    @Test
    fun aVideoMessagePlays() {
        signIn()
        waitFor(By.text(NOTIFYING_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(NOTIFYING_CHAT))
        waitFor(By.textContains("ButtonGroup"), "the conversation")

        // Through the media grid rather than up the conversation, and that is
        // not a shortcut around a flaky scroll: the demo chat speaks every
        // twenty-five seconds and the list jumps to each new message, so
        // anything reached by scrolling back is snatched away mid-tap. The
        // grid holds still, and reaching a video from it is a path a person
        // takes too.
        tap(By.desc("Photos in this chat"))
        val poster = By.descContains(VIDEO_CAPTION)
        waitFor(poster, "the video in the media grid")
        tap(poster)

        // Pause, not Play: the button shows what it will do next, so a pause
        // icon is the player saying it is running.
        waitFor(By.desc("Pause"), "the player, playing")

        // And the clock proves it, since a pause icon over a black rectangle
        // would satisfy everything above. Polled, because the label is in
        // seconds and legitimately reads 0:00 for the whole first one.
        val clock = By.textContains(" / ")
        assertTrue(
            "the player never showed a time",
            device.wait(Until.hasObject(clock), STEP_TIMEOUT)
        )
        var label: String? = null
        val deadline = System.currentTimeMillis() + PLAYBACK_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            label = device.findObject(clock)?.text
            if (label != null && !label.startsWith("0:00")) break
            SystemClock.sleep(300)
        }
        screenshot("14-video")
        assertTrue(
            "the clip did not advance: $label",
            label != null && !label.startsWith("0:00")
        )

        // And the other video, whose file has not arrived: opening it starts
        // a download, and what the player shows while that runs is the bar
        // this test is really here for.
        device.pressBack()
        waitFor(By.descContains("Composer, one take"), "the second video")
        tap(By.descContains("Composer, one take"))
        waitFor(By.textStartsWith("Downloading"), "the download's own progress")
        screenshot("15-downloading")
    }

    /**
     * A group made from the pencil, and a chat joined through a link.
     *
     * Both end in a conversation that did not exist when the test started,
     * which is the only assertion that proves either worked: a form that
     * accepted a name and went nowhere would pass anything weaker.
     */
    @Test
    fun aGroupIsMadeAndALinkIsJoined() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")

        // The pencil opens a FAB menu; the group is one of its items.
        tap(By.desc("Compose"))
        waitFor(By.text("New group"), "the FAB menu")
        screenshot("24-fab-menu")
        tap(By.text("New group"))
        waitFor(By.text("Group name"), "the new group form")
        type(NEW_GROUP_NAME)
        tap(By.text("Lina Park"))
        screenshot("16-new-group")
        // The accessibility tree as UiAutomator sees it, kept with the
        // screenshots. The create button is plainly on screen and yet was
        // found neither by its text nor by a description — and whatever hides
        // it from UiAutomator hides it from TalkBack too, which is worth
        // knowing rather than routing around.
        TestStorage().openOutputFile("hierarchy-new-group.xml").use { out ->
            device.dumpWindowHierarchy(out)
        }
        // The button has to be something TalkBack can name. The alpha's
        // extended button published an empty node, which this would catch.
        assertTrue(
            "the create button has no accessible name",
            device.wait(Until.hasObject(By.desc("Create")), DIALOG_TIMEOUT)
        )
        // Done on the keyboard creates a group, which is the path this takes.
        device.pressEnter()
        waitFor(By.text("You created the group"), "the new group's first line")

        device.pressBack()
        waitFor(By.text(NEW_GROUP_NAME), "the new group in the chat list")

        tap(By.desc("Compose"))
        tap(By.text("Join with a link"))
        type("t.me/joinchat/ExpressiveDesignClub")
        waitFor(By.text("Expressive Design Club"), "the link's preview")
        screenshot("17-join-link")
        tap(By.text("Join group"))
        waitFor(By.text("You joined the group"), "the joined group")
    }

    /**
     * The info screen behind the conversation's header.
     *
     * Reached the way a person reaches it — by tapping the name at the top of
     * a chat — because a route nothing navigates to is a screen nobody can
     * open. What it proves is that the members the header already knows
     * about arrive here as rows, and that the way out of the group is on it.
     */
    @Test
    fun theChatHeaderOpensInfo() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")

        // The header, which is the title inside the app bar rather than the
        // row in the list behind it — and only once nothing is over it. A
        // heads-up notification lands across the app bar, and a tap that
        // reaches it opens the chat that sent it: this test failed by
        // arriving in "Material Design" and photographing it.
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.text("Info"), "the info screen")
        waitFor(By.text("Members"), "the member list")
        waitFor(By.textContains("Leave"), "the way out of the group")
        screenshot("12-chat-info")
    }

    /**
     * A story opens and stays open.
     *
     * The viewer used to close itself the instant it opened: its state began
     * as "no story", and the route popped on that before the story had been
     * looked up. The demo never showed it, because nothing here opened a
     * story at all. Waiting for the caption proves the viewer is up; taking
     * the screenshot a beat later proves it stayed.
     */
    @Test
    fun aStoryOpensAndStaysOpen() {
        signIn()
        waitFor(By.text(STORY_AUTHOR), "the stories rail")
        awaitNoHeadsUp()
        tap(By.text(STORY_AUTHOR))
        waitFor(By.text(STORY_CAPTION), "the story viewer")
        Thread.sleep(1_500)
        assertTrue(
            "the story closed by itself",
            device.hasObject(By.text(STORY_CAPTION))
        )
        screenshot("21-story")
    }

    /**
     * The chat list's overflow menu reaches Saved Messages and the proxies,
     * and a proxy added there is listed.
     *
     * Saved Messages is proved by the composer: the menu item and the chat
     * row share a name, and only the conversation has a message field.
     */
    @Test
    fun theOverflowMenuOpensSavedMessagesAndProxies() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()

        tap(By.desc("More"))
        tap(By.text("Saved Messages"))
        waitFor(By.text("Message"), "the Saved Messages conversation")
        screenshot("22-saved-messages")
        device.pressBack()

        waitFor(By.text("Material Design"), "the chat list again")
        tap(By.desc("More"))
        tap(By.text("Proxy"))
        waitFor(By.text("Use proxy"), "the proxy screen")
        // By its description: the alpha's extended FAB publishes no text.
        tap(By.desc("Add proxy"))
        waitFor(By.text("Save and connect"), "the add-proxy sheet")
        // Server, port and secret, in that order down the sheet.
        val fields = device.findObjects(By.clazz("android.widget.EditText"))
        assertTrue("the sheet has ${fields.size} fields", fields.size >= 3)
        fields[0].text = PROXY_SERVER
        fields[1].text = "443"
        fields[2].text = "dd" + "0123456789abcdef".repeat(2)
        device.waitForIdle(IDLE_TIMEOUT)
        tap(By.text("Save and connect"))
        waitFor(By.text("$PROXY_SERVER:443"), "the added proxy")
        waitFor(By.textContains(" ms"), "the proxy's ping")
        screenshot("23-proxy")
    }

    /**
     * Settings → About → Check for updates asks GitHub and comes back with
     * an answer. Which answer depends on the release page and on whether the
     * emulator is online, so any of the three is a pass — what fails is the
     * row never getting past "Checking", or the app falling over on the way.
     */
    @Test
    fun checkingForUpdatesComesBackWithAnAnswer() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        val row = By.text("Check for updates")
        // Near the bottom of Settings, which may be below the fold on a
        // small screen.
        repeat(3) {
            if (device.hasObject(row)) return@repeat
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
        }
        tap(row)
        val answer = By.text(Pattern.compile(".*(newest there is|is out|Could not reach GitHub).*"))
        waitFor(answer, "an answer from the update check")
        screenshot("25-updates")
    }

    /**
     * Folder tabs, which are a filter and have to be seen to filter.
     *
     * The demo backend seeds three folders with overlapping membership, so
     * this can be checked with no account: tapping "People" should leave the
     * group behind and keep the private chats. A tab strip that switched the
     * indicator and showed the same list would pass every weaker assertion
     * than this one.
     */
    @Test
    fun folderTabsFilterTheChatList() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        screenshot("10-folders")

        tap(By.text("People"))
        waitFor(By.text("Mom"), "a chat in the People folder")
        assertNull(
            "the group is not in People and should have gone",
            device.findObject(By.text(GROUP_CHAT))
        )
        screenshot("11-folder-people")

        // And back, because a filter you cannot undo is a trap.
        awaitNoHeadsUp()
        tap(By.text("All"))
        waitFor(By.text(GROUP_CHAT), "the whole list again")

        // Sideways on the list itself, because the folders are pages as well
        // as tabs. All, Work, People: one swipe leaves Mom behind, who is
        // only in People, and a second one finds her again and leaves the
        // group, which is only in Work. A pager that moved and showed the
        // same list twice would fail one of the two.
        swipeListLeft()
        assertTrue(
            "one swipe should have reached Work, where Mom is not",
            device.wait(Until.gone(By.text("Mom")), STEP_TIMEOUT)
        )
        waitFor(By.text(GROUP_CHAT), "the group, in Work")
        screenshot("19-folder-swiped")
        swipeListLeft()
        waitFor(By.text("Mom"), "Mom, in People")
        // Waited for rather than looked up once. Mom arrives with the first
        // pixels of People, while Work is still sliding out beside her — a
        // lookup at that moment finds the group, and the node is gone by the
        // time the failure message asks it what it was.
        assertTrue(
            "the second swipe should have left Work and its group behind",
            device.wait(Until.gone(By.text(GROUP_CHAT)), STEP_TIMEOUT)
        )
    }

    /**
     * The header — name, folders, stories — goes as the chats scroll down
     * and comes back as soon as they scroll up.
     *
     * Asserted through the name, which leaves the accessibility tree once the
     * header has shrunk to nothing and faded out. The tick that goes with it
     * is felt, not seen, and nothing here can check it.
     */
    @Test
    fun theHeaderScrollsAwayAndBack() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()

        dragList(fromY = 0.75, toY = 0.35)
        assertTrue(
            "scrolling the chats down should take the header away",
            device.wait(Until.gone(By.text(APP_TITLE)), STEP_TIMEOUT)
        )
        screenshot("18-header-hidden")

        dragList(fromY = 0.45, toY = 0.8)
        waitFor(By.text(APP_TITLE), "the header, back after scrolling up")
    }

    /**
     * The attachment sheet, which now asks for something before it opens.
     *
     * The carousel of recent pictures reads the media library, and on this
     * emulator — Android 14, no photos on it — the interesting part is not
     * the strip, which has nothing to draw. It is the dialog: the sheet
     * requests the permission as it appears, and a dialog that is answered
     * and then leaves a broken sheet behind, or one that is never dismissed,
     * would strand the one route to sending a photo.
     *
     * So this proves the rows are still reachable with the question answered,
     * which is the failure a person would otherwise find on their own phone.
     */
    @Test
    fun theAttachmentSheetOpens() {
        // Before anything is signed in, so the pictures are already in the
        // library by the time the sheet reads it.
        seedGallery()

        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")

        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")

        tap(By.desc("Attach"))
        allowPhotos()
        waitFor(By.text("Photo or video"), "the attachment sheet")
        // The carousel, not just the sheet around it. Without this the test
        // would pass on a build where the strip never drew — which is what
        // it looked like before the gallery was seeded, and is
        // indistinguishable from a device that simply has no photos.
        waitFor(By.desc("Recent photo"), "the recent-photo carousel")
        screenshot("09-attachments")
    }

    /**
     * Puts a few pictures into the device's library.
     *
     * A fresh emulator has an empty gallery, so the carousel correctly draws
     * nothing and the interesting half of this feature goes unwatched.
     * Inserting through MediaStore needs no permission — an app may always
     * write its own media — and these are owned by the app under test, which
     * is also the one that reads them back.
     *
     * Flat colours rather than anything photographic: what is being proved is
     * that the strip is populated and masked, and a screenshot of three
     * plain squares says that more legibly than a picture would.
     */
    private fun seedGallery(count: Int = 4) {
        val resolver = InstrumentationRegistry.getInstrumentation()
            .targetContext.contentResolver
        val colours = intArrayOf(
            Color.rgb(0xE9, 0x6D, 0x3F),
            Color.rgb(0x4E, 0x8C, 0xD6),
            Color.rgb(0x6C, 0xC2, 0x77),
            Color.rgb(0xB3, 0x6B, 0xD1)
        )
        repeat(count) { index ->
            val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(colours[index % colours.size])
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "smoke-$index.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /**
     * Waits for a heads-up notification to retreat into the status bar.
     *
     * The demo chat speaks every twenty-five seconds, and its heads-up lands
     * across the top of the screen — over the folder tabs. UiAutomator still
     * finds a tab underneath it and still clicks where it is, so the tap goes
     * to the notification and the list never changes: the first run of this
     * test failed on exactly that, with a screenshot of an unfiltered list
     * and a notification across the top of it.
     *
     * Anchored on the Reply action rather than on the chat's name, which the
     * list behind it also carries — waiting for that to go would wait for the
     * whole twenty seconds and then carry on anyway.
     */
    private fun awaitNoHeadsUp() {
        device.wait(
            Until.gone(By.text(Pattern.compile("reply", Pattern.CASE_INSENSITIVE))),
            HEADS_UP_TIMEOUT
        )
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /**
     * Answers the photo permission dialog, if one is up.
     *
     * Android 14 offers three buttons, and "Don't allow" contains the word
     * the notification dialog is matched on — so this matches the whole
     * label rather than a substring, and "Allow all" is the only thing that
     * satisfies it. Granting rather than refusing: a refusal is the path
     * where the carousel draws nothing, which is exactly what an emulator
     * with an empty library shows anyway, so it would prove less.
     *
     * Short, and absence is not a failure: whether the dialog appears at all
     * depends on the platform version and on what an earlier test already
     * granted.
     */
    private fun allowPhotos() {
        val allow = By.text(
            Pattern.compile("allow all|allow", Pattern.CASE_INSENSITIVE)
        )
        if (device.wait(Until.hasObject(allow), DIALOG_TIMEOUT)) {
            device.findObject(allow)?.click()
            device.waitForIdle(IDLE_TIMEOUT)
        }
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
     * One message, one line in its notification — after the screen has been
     * turned.
     *
     * The activity starts the notification service in onCreate, so each
     * rotation starts it again, and each start used to add another listener
     * for new messages. Two rotations, three listeners, and a message could
     * land in the shade three times over.
     *
     * Read from the app's own notifications rather than from the shade: the
     * shade collapses a conversation to its latest line, so a duplicate can
     * be there and not be on screen. The notification's MessagingStyle holds
     * every line it was given.
     */
    @Test
    fun aMessageIsNotifiedOnceAfterTurningTheScreen() {
        signIn()
        waitFor(By.text(NOTIFYING_CHAT), "the chat list")

        device.setOrientationLeft()
        device.waitForIdle(IDLE_TIMEOUT)
        device.setOrientationNatural()
        device.waitForIdle(IDLE_TIMEOUT)
        waitFor(By.text(NOTIFYING_CHAT), "the chat list, after turning the screen")

        // A clean slate for this chat. The app's process outlives each test,
        // and so do its notifications — an entry left by an earlier test,
        // with the demo's four lines going round, could repeat a line
        // honestly and fail this for nothing.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSystemService(NotificationManager::class.java)
            .cancel(PostedNotifications.notificationId(NOTIFYING_CHAT_ID))
        PostedNotifications.clear(NOTIFYING_CHAT_ID)

        device.pressHome()
        device.waitForIdle(IDLE_TIMEOUT)
        waitForNotification()
        screenshot("20-notification-after-rotation")

        val lines = notifiedLines(NOTIFYING_CHAT)
        assertTrue("the notification for $NOTIFYING_CHAT has no lines", lines.isNotEmpty())
        assertEquals("a line posted more than once: $lines", lines.distinct(), lines)
    }

    /** The lines of this app's notification for [chatTitle], oldest first. */
    private fun notifiedLines(chatTitle: String): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications
            .mapNotNull {
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification)
            }
            .firstOrNull { it.conversationTitle?.toString() == chatTitle }
            ?.messages
            ?.map { it.text.toString() }
            .orEmpty()
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
        if (!loginIsUp) {
            // Signed in already, by an earlier test in this process — but the
            // permission dialog may still be up if that test failed before
            // answering it, and it covers everything the next test looks for.
            // That is how one flaky test once failed all twelve.
            allowNotifications()
            return
        }
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

        // Sent on its fifth digit, like every Telegram client: the button
        // is only there for a code whose length the server did not give.
        type("12345")
        // Usually gone already: the code goes on its fifth digit, and the
        // button can vanish between being found and being pressed.
        try {
            device.findObject(By.text("Sign in"))?.click()
        } catch (_: StaleObjectException) {
        }

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

    /**
     * Scrolls back up the conversation until [selector] is on screen.
     *
     * A chat opens at its newest message, and anything seeded earlier is
     * above the fold — more so here, because the demo chat adds a line every
     * twenty-five seconds while the test is running.
     */
    private fun scrollBackTo(selector: BySelector, what: String) {
        if (device.hasObject(selector)) return
        val list = device.findObject(By.scrollable(true))
        // Small steps, and plenty of them. The demo chat adds a line every
        // twenty-five seconds, so by the time this test runs the video can be
        // a dozen messages above the fold — and a long stride can carry it
        // past between two checks, which is how this failed while finding it
        // perfectly well one run earlier.
        repeat(14) {
            list?.scroll(Direction.UP, 0.4f)
            device.waitForIdle(IDLE_TIMEOUT)
            if (device.hasObject(selector)) return
        }
        screenshot("failed-scrolling-to-${what.replace(' ', '-')}")
        fail("$what never came into view")
    }

    /** A vertical drag through the middle of the screen, which is the list. */
    private fun dragList(fromY: Double, toY: Double) {
        val x = device.displayWidth / 2
        val h = device.displayHeight
        device.swipe(x, (h * fromY).toInt(), x, (h * toY).toInt(), 25)
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /**
     * Right to left across the lower half, over the chats rather than the
     * tabs — the swipe being tested is the one on the list.
     */
    private fun swipeListLeft() {
        val y = (device.displayHeight * 0.65).toInt()
        device.swipe(
            (device.displayWidth * 0.85).toInt(), y,
            (device.displayWidth * 0.15).toInt(), y,
            15
        )
        device.waitForIdle(IDLE_TIMEOUT)
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

    /**
     * Taps [selector], retrying once.
     *
     * The retry is not superstition: the demo chat inserts a message every
     * twenty-five seconds, which re-lays out whatever is on screen, and a
     * node found by `wait` can be gone by the time `findObject` asks for it
     * again. That raced twice on the chat header in one evening.
     *
     * And it photographs the screen before giving up, because "nothing to
     * tap" says nothing at all about what was there instead.
     */
    private fun tap(selector: BySelector) {
        repeat(2) {
            device.wait(Until.hasObject(selector), STEP_TIMEOUT)
            val node = device.findObject(selector)
            if (node != null) {
                node.click()
                device.waitForIdle(IDLE_TIMEOUT)
                return
            }
            device.waitForIdle(IDLE_TIMEOUT)
        }
        // The selector's own text is full of quotes, backslashes and brackets,
        // which TestStorage refuses as a file name — the first time this
        // fired it threw over the name and hid the failure it was reporting.
        screenshot("failed-tapping-" + selector.toString().filter { it.isLetterOrDigit() })
        error("nothing to tap: $selector")
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
        // Thirty rather than twenty: a cold start on a CI emulator that has
        // just rebuilt every window is slower than one on an idle device,
        // and the difference was a failure rather than a wait.
        const val LAUNCH_TIMEOUT = 30_000L
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
        /** Its id in the demo backend, where it is the first chat seeded. */
        const val NOTIFYING_CHAT_ID = 1L

        /** systemui, which owns the shade and the reply field in it. */
        const val SYSTEM_UI = "com.android.systemui"

        /** Distinctive enough that finding it cannot be a coincidence. */
        const val REPLY_TEXT = "Replied from the shade"

        /**
         * Long enough for a four-second clip to leave its first second
         * behind, short enough that a stalled player is still a quick answer.
         */
        const val PLAYBACK_TIMEOUT = 6_000L

        /**
         * The caption on the demo video that has a file behind it, which is
         * also what its poster is published as.
         */
        const val VIDEO_CAPTION = "Expressive motion, slowed down"

        /** Distinctive enough that finding it in the list cannot be luck. */
        const val NEW_GROUP_NAME = "Smoke test crit"

        /** The seeded group with more than one person talking in it. */
        const val GROUP_CHAT = "Design Circle"

        /**
         * A circle in the demo's stories rail, and its story's caption.
         * "Circle" rather than a person: every person in the rail also has a
         * chat of the same name, and a tap by that name can land on the row.
         */
        const val STORY_AUTHOR = "Circle"

        /** A proxy the demo pretends to reach. */
        const val PROXY_SERVER = "proxy.example.org"
        const val STORY_CAPTION = "Palette drop"
        const val APP_TITLE = "TelegramYou"

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
