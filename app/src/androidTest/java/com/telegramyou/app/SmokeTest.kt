package com.telegramyou.app

import android.os.Build
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
import org.junit.Assert.assertFalse
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

        if (!device.wait(Until.hasObject(By.pkg(packageName).depth(0)), LAUNCH_TIMEOUT)) {
            // What was on screen instead — the launcher, a system dialog, an
            // "isn't responding" — which the bare assertion never said.
            screenshot("failed-launching")
            fail("the app never drew anything")
        }
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
        // And opened at its latest line, which a conversation laid out from
        // the top and scrolled down once loaded did not reliably do. Not by
        // looking for the newest message's words: this chat speaks on a
        // timer, so which message is newest depends on how long the run has
        // taken. The jump-to-latest button is up exactly when the list is
        // not at its end, so its absence is the thing itself.
        device.waitForIdle(IDLE_TIMEOUT)
        assertFalse(
            "the conversation opened away from its latest message",
            device.hasObject(By.desc("Jump to latest"))
        )
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
     * A search hit far older than anything opening the chat loads: tapped,
     * the history around it replaces the latest messages and the hit is on
     * screen; the jump button then brings the latest back. The demo group
     * carries months of history before its visible conversation for this,
     * and its very first line is the one searched for.
     */
    @Test
    fun anOldSearchHitOpensWhereItIs() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")
        tap(By.desc("Search in chat"))
        val field = By.clazz("android.widget.EditText").focused(true)
        waitFor(field, "the search field")
        device.findObject(field).text = OLD_HIT_QUERY
        // The result row; the words are bold inside it, so by containment.
        waitFor(By.textContains("first sketches"), "the old search hit")
        tap(By.textContains("first sketches"))
        // The results close first, so the line found after that is the
        // message in the conversation and not its row in the results.
        device.wait(Until.gone(field), STEP_TIMEOUT)
        waitFor(By.textContains("first sketches"), "the old message in the conversation")
        assertFalse(
            "the latest messages should have given way to the history around the hit",
            device.hasObject(By.textContains("Reviewing tonight"))
        )
        screenshot("28-old-search-hit")
        tap(By.desc("Jump to latest"))
        waitFor(By.textContains("Reviewing tonight"), "the latest messages again")
    }

    /**
     * A GIF and a round video message, the two kinds of video people send
     * most, which the chat used to show as a word each. The GIF is on screen
     * and playing by itself; the circle plays on a tap and pauses on another.
     * What is checked is that each is there and answers — how either looks
     * is for a person to judge.
     */
    @Test
    fun gifsAndVideoMessagesPlayInPlace() {
        signIn()
        waitFor(By.text("Lina Park"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        waitFor(By.desc("GIF"), "the GIF")
        val note = By.descStartsWith("Video message, ")
        waitFor(note, "the round video message")
        tap(note)
        // Looked for often rather than waited for: the demo's clip is four
        // seconds long, and on the emulator a wait that polls once a second
        // over a slow tree missed it playing — the audio in the log shows
        // it did play, from the tap to its end.
        val playingNote = By.desc("Video message, playing")
        val deadline = SystemClock.uptimeMillis() + STEP_TIMEOUT
        // And through a fresh accessibility cache each look: UiAutomator's
        // held the circle's old description while it played — the audio in
        // the log runs from the tap to the clip's end — the same staleness
        // the folder pager showed.
        while (SystemClock.uptimeMillis() < deadline) {
            if (Build.VERSION.SDK_INT >= 34) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            if (device.hasObject(playingNote)) break
            SystemClock.sleep(100)
        }
        waitFor(playingNote, "the video message playing")
        screenshot("31-video-note")
        // Paused by a second tap, or over by itself if the four seconds ran
        // out first — either way it comes back to rest on its length.
        try {
            device.findObject(playingNote)?.click()
        } catch (_: StaleObjectException) {
        }
        waitFor(By.desc("Video message, 0:08"), "the video message paused")
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
     * A video goes into picture-in-picture from the player's button: the
     * window manager is asked whether the app's task is pinned, since the
     * small window is the system's and not something the app can be looked
     * into for. The app is opened again at the end, full size.
     */
    @Test
    fun aVideoGoesPictureInPicture() {
        signIn()
        waitFor(By.text(NOTIFYING_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(NOTIFYING_CHAT))
        waitFor(By.textContains("ButtonGroup"), "the conversation")
        tap(By.desc("Photos in this chat"))
        val poster = By.descContains(VIDEO_CAPTION)
        waitFor(poster, "the video in the media grid")
        tap(poster)
        waitFor(By.desc("Pause"), "the player, playing")
        tap(By.desc("Picture in picture"))
        val deadline = SystemClock.uptimeMillis() + STEP_TIMEOUT
        var pinned = false
        while (!pinned && SystemClock.uptimeMillis() < deadline) {
            pinned = device.executeShellCommand("dumpsys activity activities").contains("mode=pinned")
            if (!pinned) SystemClock.sleep(500)
        }
        screenshot("69-picture-in-picture")
        assertTrue("the video never went into picture-in-picture", pinned)
        launchApp()
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

        // The speed steps on a tap, and says so.
        tap(By.desc("Playback speed 1×"))
        waitFor(By.desc("Playback speed 1.5×"), "the video at one and a half")
        tap(By.desc("Playback speed 1.5×"))
        tap(By.desc("Playback speed 2×"))
        tap(By.desc("Playback speed 0.5×"))
        waitFor(By.desc("Playback speed 1×"), "the speed back to normal")

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
        screenshot("12-chat-info")

        // The chat's notifications: off for an hour, and the row says until
        // when. Scrolled to, because in a group they sit under the header.
        val muteFor = By.text("Mute for…")
        repeat(3) {
            if (device.wait(Until.hasObject(muteFor), SHORT_WAIT)) return@repeat
            try {
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f)
            } catch (_: StaleObjectException) {
            }
        }
        tap(muteFor)
        tap(By.text("1 hour"))
        waitFor(By.textStartsWith("Off until"), "the chat muted for an hour")
        screenshot("27-chat-notifications")

        // The way out, under Delete all my messages, and the members after
        // it — both below the first screen since the invite link grew its
        // Share and Revoke. Looked for going down, after the notifications
        // above them.
        scrollDownTo(By.textContains("Leave"))
        scrollDownTo(By.text("Members"))
    }

    /**
     * A story posted from "My story": a recent photo from the strip,
     * contacts only, Post — and the screen goes back to the list.
     */
    @Test
    fun aStoryIsPostedFromMyStory() {
        signIn()
        waitFor(By.text("My story"), "the stories rail")
        awaitNoHeadsUp()
        tap(By.text("My story"))
        waitFor(By.text("New story"), "the new story screen")
        allowPhotos()
        waitFor(By.desc("Recent photo"), "a recent photo to post")
        // The widest of the carousel's photos, not the first found: that was
        // the sliver at the right edge, and its centre is off the screen.
        device.findObjects(By.desc("Recent photo"))
            .maxByOrNull { it.visibleBounds.width() }!!
            .click()
        waitFor(By.desc("Story picture"), "the picture chosen")
        // No caption typed: the keyboard would stand over the buttons
        // below it, and a caption is optional.
        tap(By.text("My contacts"))
        screenshot("68-new-story")
        tap(By.text("Post story"))
        waitFor(By.text("Material Design"), "the chat list after posting")
        // Up in the rail beside the add entry, and it opens like anybody's.
        waitFor(By.text("Add story"), "the add entry renamed")
        tap(By.text("My story"))
        waitFor(By.desc("Story"), "the story just posted")
        screenshot("75-my-story")
        device.pressBack()
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
     * Settings → App update → Check for updates asks GitHub and comes back with
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
        // Settings itself before anything is looked for in it: the first
        // run went for "the scrollable thing" while the chat list was still
        // leaving, and scrolled a list that was gone by the time it moved.
        waitFor(By.text("Appearance"), "the settings")
        screenshot("41-settings")
        // Its own screen now, as Android's System update is: the row near the
        // bottom of Settings, which may be below the fold on a small screen.
        scrollSettingsTo(By.text("App update"))
        tap(By.text("App update"))
        // One card now, headed for this version or for the one waiting.
        waitFor(By.textStartsWith("What's new in"), "the update screen and its notes")
        // The quiet check at launch may already have found a newer build —
        // on CI it usually has, since the published APK outnumbers the UI
        // workflow's own — in which case the screen offers it and there is
        // nothing to press. Otherwise the button asks, and either outcome is
        // an answer.
        val answer = By.text(Pattern.compile(".*(up to date|is out|is ready|Could not reach GitHub).*"))
        if (!device.wait(Until.hasObject(answer), SHORT_WAIT)) {
            try {
                device.findObject(By.text("Check for updates"))?.click()
            } catch (_: StaleObjectException) {
            }
        }
        waitFor(answer, "an answer from the update check")
        screenshot("25-updates")
    }

    /**
     * Signing in by QR code: out of the account, the phone step's other way
     * in, the code on screen — and in, when the demo "scans" it by itself a
     * few seconds later, the way a phone scanning it would let a live one in.
     */
    @Test
    fun signsInWithAQrCode() {
        signIn()
        logOut()
        // The proxies are reachable before signing in — where Telegram is
        // blocked, nothing else on this screen works without one.
        tap(By.desc("Proxy"))
        waitFor(By.text("Use proxy"), "the proxy screen from the login screen")
        device.pressBack()
        waitFor(By.text("Your phone"), "the login screen again")
        tap(By.text("Log in with a QR code"))
        waitFor(By.desc("QR code to sign in"), "the QR code")
        screenshot("26-qr-login")
        waitFor(By.text("Material Design"), "the chat list after the scan")
    }

    /**
     * Signing in where Telegram asks for a login email first: the address,
     * the code sent to it, and the way out for a mailbox that is gone. The
     * demo takes that road for a number ending in five nines, since there is
     * no real account here to be asked.
     */
    @Test
    fun signsInWithALoginEmail() {
        signIn()
        logOut()
        type("+19999999999")
        tap(By.text("Continue"))
        waitFor(By.text("Add a login email"), "the email step")
        type("me@example.org")
        tap(By.text("Send code"))
        waitFor(By.text("Check your email"), "the email code step")
        waitFor(By.textContains("m***@example.org"), "where the code went")
        screenshot("27-email-code")
        // The reset says how long it takes before it is pressed, and what
        // is pending once it has been.
        tap(By.text("Reset email (takes 7 days)"))
        waitFor(By.text("Email resets in 7 days"), "the pending reset")
        type("12345")
        waitFor(By.text("Material Design"), "the chat list after the email code")
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
        // A second swipe carries on past Work: the group, which is only in
        // Work, leaves. Where exactly it lands is not asserted — on the
        // emulator a fling has carried one swipe past People to News, and
        // the loop that tried to steer back from what was on screen missed
        // twice. That the pager moves on each swipe is the point here.
        // Once more if the first one did nothing: a message from the demo's
        // chatter landing mid-gesture can leave the pager where it was.
        //
        // The first try is in the empty space under Work's three chats, the
        // second across the rows themselves, and which one moved the pager
        // is written to evidence/frames/: a swipe on the empty part of a
        // short folder is a thing a person does too.
        val tries = StringBuilder()
        var left = false
        for ((attempt, height) in listOf(0.65, 0.35).withIndex()) {
            swipeListLeft(height)
            left = goneAfterFreshLooks(By.text(GROUP_CHAT), STEP_TIMEOUT / 2)
            tries.append("swipe ${attempt + 1} at ${(height * 100).toInt()}% of the height: ")
                .append(if (left) "left Work" else "stayed on Work").append('\n')
            if (left) break
            screenshot("folder-swipe-${attempt + 1}-stayed")
            // What the accessibility tree still holds, bounds and all: the
            // group's row has been reported with its page off screen.
            TestStorage().openOutputFile("hierarchy-folder-swipe-${attempt + 1}.xml").use { out ->
                device.dumpWindowHierarchy(out)
            }
        }
        TestStorage().openOutputFile("frames-folder-swipe.txt").use { it.write(tries.toString().toByteArray()) }
        assertTrue("the second swipe should have left Work and its group behind", left)
        // And the tabs and pages agree: People by its tab, Mom there.
        tap(By.text("People"))
        waitFor(By.text("Mom"), "Mom, in People")
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
        // The pinned chat at the top, not the group further down: groups
        // made by other tests on the same emulator push that below the fold.
        waitFor(By.text("Material Design"), "the chat list")
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
        // The notification permission granted from the shell before anything
        // asks for it, as location is: answering the system dialog raced it,
        // and on a fresh emulator the dialog could arrive after the look for
        // it had given up, and stand over the chat list for the whole test.
        if (Build.VERSION.SDK_INT >= 33) {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            device.executeShellCommand("pm grant $app android.permission.POST_NOTIFICATIONS")
        }
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
        var polls = 0
        while (System.currentTimeMillis() < deadline) {
            // The demo chat asked to speak now, and again every other poll,
            // rather than waited for on its twenty-five-second timer: that
            // wait, up to twice over, was most of what these tests took.
            if (polls++ % 2 == 0) demoSpeakNow()
            device.openNotification()
            if (device.wait(Until.hasObject(entry), SHADE_POLL)) return
        }
        screenshot("failed-waiting-for-the-notification")
        fail("no notification from $NOTIFYING_CHAT ever arrived")
    }

    /** The demo chat's next line, now; this test runs in the app's process. */
    private fun demoSpeakNow() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        (app as? TelegramYouApp)?.demoClient?.speakNow()
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
            // A moment, not a glance: the list can still be settling after
            // the drag, and a check made mid-glide missed a line that was
            // plainly on screen in the photograph taken straight after.
            if (device.wait(Until.hasObject(selector), 1_000)) return
        }
        screenshot("failed-scrolling-to-${what.replace(' ', '-')}")
        fail("$what never came into view")
    }

    /** A vertical drag through the middle of the screen, which is the list. */
    /** Appearance → Mini player at the top, switched over; back on Settings. */
    private fun setMiniPlayerOnTop() {
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        tap(By.text("Appearance"))
        scrollDownTo(By.text("Mini player at the top"))
        tap(By.text("Mini player at the top"))
        device.pressBack()
        waitFor(By.text("Appearance"), "the settings again")
    }

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
    private fun swipeListRight() {
        val y = (device.displayHeight * 0.65).toInt()
        device.swipe(
            (device.displayWidth * 0.15).toInt(), y,
            (device.displayWidth * 0.85).toInt(), y,
            15
        )
        device.waitForIdle(IDLE_TIMEOUT)
    }

    private fun swipeListLeft(height: Double = 0.65) {
        val y = (device.displayHeight * height).toInt()
        device.swipe(
            (device.displayWidth * 0.85).toInt(), y,
            (device.displayWidth * 0.15).toInt(), y,
            15
        )
        device.waitForIdle(IDLE_TIMEOUT)
    }

    private fun waitFor(selector: BySelector, what: String) {
        // Looked for through a fresh tree each time, not waited for through
        // UiAutomator's cache: three times on one day it held a screen
        // without what was plainly on it — "Soft", a chat's header, "31
        // votes" under a poll that had counted the vote — and failed a test
        // on something that had worked.
        val found = lookFresh(selector)
        if (!found) {
            // The shot is taken before failing, because what is on screen
            // instead is the whole question.
            screenshot("failed-waiting-for-${what.replace(' ', '-')}")
        }
        assertTrue("$what never appeared", found)
    }

    /** Whether [selector] turns up within [STEP_TIMEOUT], looked for as waitFor looks. */
    private fun lookFresh(selector: BySelector): Boolean {
        val deadline = SystemClock.uptimeMillis() + STEP_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            if (Build.VERSION.SDK_INT >= 34) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            if (device.wait(Until.hasObject(selector), FRESH_LOOK_MILLIS)) return true
        }
        return false
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
     *
     * Looked for through a fresh tree, as waitFor does: the video player's
     * speed button read "0.5×" on screen for forty seconds while the cached
     * tree still said "2×", and a tap on 0.5× found nothing.
     */
    private fun tap(selector: BySelector) {
        repeat(2) {
            lookFresh(selector)
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
    /**
     * Settings' two places to check rather than change: the devices this
     * account is signed in on, one of them ended; and the cache, cleared.
     * The demo has a stranger's client among its sessions and two gigabytes
     * of files, so both have something to act on.
     */
    @Test
    fun devicesAndStorageFromSettings() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")

        scrollSettingsTo(By.text("Devices"))
        tap(By.text("Devices"))
        waitFor(By.text("Galaxy S21"), "the unofficial session")
        screenshot("29-devices")
        tap(By.text("Galaxy S21"))
        waitFor(By.text("End this session?"), "the confirmation")
        tap(By.text("End session"))
        assertTrue(
            "the ended session should leave the list",
            device.wait(Until.gone(By.text("Galaxy S21")), STEP_TIMEOUT)
        )
        device.pressBack()
        // The row just left rather than the top: Settings is still scrolled
        // to where Devices was, and Appearance may be above the fold.
        waitFor(By.text("Devices"), "the settings again")

        scrollSettingsTo(By.text("Data and storage"))
        tap(By.text("Data and storage"))
        waitFor(By.text("Videos"), "the cache by kind")
        screenshot("30-storage")
        // The button names its size; the dialog's title ends in a question
        // mark. One pattern: a selector takes a single text condition.
        tap(By.text(Pattern.compile("Clear [0-9.]+ GB")))
        tap(By.text("Clear"))
        // The cleared kinds leaving the list, rather than the snackbar that
        // says so: it is on screen four seconds, and the emulator's slow
        // polls have missed it while the clearing itself had worked.
        assertTrue(
            "the cleared videos should have left the list",
            device.wait(Until.gone(By.text("Videos")), STEP_TIMEOUT)
        )
        assertTrue(
            "the cleared videos should leave the list",
            device.wait(Until.gone(By.text("Videos")), STEP_TIMEOUT)
        )
    }

    /**
     * Privacy: each rule says who it is set to, and choosing another audience
     * in its dialog changes the row. The demo's last-seen rule carries two
     * exceptions made elsewhere, which the row has to show. Text size, now
     * on Appearance, is checked there; this was the place it was offered.
     * checked for being offered; how large is for a person to judge.
     */
    @Test
    fun privacyRulesChangeAndTextSizeIsOffered() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")

        scrollSettingsTo(By.text("Privacy"))
        tap(By.text("Privacy"))
        waitFor(By.text("Phone number"), "the privacy rules")
        waitFor(By.text("Everybody (−2)"), "last seen with its exceptions")
        tap(By.text("Phone number"))
        waitFor(By.text("Who can see my phone number"), "the phone number dialog")
        tap(By.text("Nobody"))
        waitFor(By.text("Nobody"), "phone number set to nobody")
        screenshot("32-privacy")
    }

    /** Scrolls the settings down until [selector] is on screen. */
    private fun scrollSettingsTo(selector: BySelector) {
        repeat(4) {
            if (device.wait(Until.hasObject(selector), SHORT_WAIT)) return
            try {
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f)
            } catch (_: StaleObjectException) {
            }
        }
    }

    /**
     * Settings → For geeks: two switches turned on, and the conversation
     * showing both — times to the second, and Details in a message's menu
     * with the message's id in it.
     */
    @Test
    fun forGeeksSwitchesReachTheChat() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        scrollSettingsTo(By.text("For geeks"))
        tap(By.text("For geeks"))
        waitFor(By.text("Seconds in message times"), "the geek settings")
        tap(By.text("Seconds in message times"))
        tap(By.text("Message details"))
        screenshot("33-for-geeks")
        device.pressBack()
        // The row just left, not the top of the list: the settings are still
        // scrolled down to it, with Appearance out of sight above.
        waitFor(By.text("For geeks"), "the settings again")

        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list again")
        tap(By.text("Material Design"))
        waitFor(By.text("Welcome to TelegramYou"), "the conversation")
        waitFor(By.text(Pattern.compile("\\d{2}:\\d{2}:\\d{2}")), "a time with seconds")
        // Pressed on a time with seconds, which is inside a bubble and on
        // screen however much the demo chat has said by now. Not on a
        // message's words: "Welcome to TelegramYou" is also the pinned bar's
        // line, first in the hierarchy and with no menu, and any other line
        // may have scrolled away under the chat's own chatter.
        val bubble = By.text(Pattern.compile("\\d{2}:\\d{2}:\\d{2}"))
        waitFor(bubble, "a message to open the menu of")
        repeat(3) {
            if (device.hasObject(By.text("Details"))) return@repeat
            try {
                device.findObject(bubble)?.longClick()
            } catch (_: StaleObjectException) {
            }
            device.wait(Until.hasObject(By.text("Details")), SHORT_WAIT)
        }
        tap(By.text("Details"))
        waitFor(By.text("Message ID"), "the message details")
    }

    /**
     * The rest of For geeks, each where it acts: stories gone from the chat
     * list, the All tab gone and the archive reached from the menu instead,
     * search opening with the keyboard down, a double tap replying, and a
     * forward arriving as a copy with no "Forwarded from".
     *
     * Set through the store rather than tapped one by one — the switches
     * themselves are the screen's, tested above — and put back however the
     * test ends: the tests share one install, and a chat list with no All
     * tab would fail every test after this one.
     */
    @Test
    fun theOtherGeekSwitchesReachTheirScreens() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        val geeks = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TelegramYouApp).geeks
        try {
            geeks.update {
                it.copy(
                    hideStories = true,
                    hideAllChatsTab = true,
                    searchWithoutKeyboard = true,
                    doubleTap = com.telegramyou.app.settings.DoubleTapAction.Reply,
                    forwardWithoutQuote = true
                )
            }
            // No stories, and no All: the first folder leads.
            waitFor(By.text("Work"), "the folders without All")
            val gone = SystemClock.uptimeMillis() + STEP_TIMEOUT
            while (SystemClock.uptimeMillis() < gone &&
                (device.hasObject(By.text("My story")) || device.hasObject(By.text("All")))
            ) {
                if (Build.VERSION.SDK_INT >= 34) {
                    InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
                }
                SystemClock.sleep(300)
            }
            screenshot("33b-geeks-no-stories-no-all")
            assertFalse("the stories stayed", device.hasObject(By.text("My story")))
            assertFalse("the All tab stayed", device.hasObject(By.text("All")))

            // The archive, whose row only the All tab carries, from the menu.
            tap(By.desc("More"))
            tap(By.text("Archived chats"))
            waitFor(By.text("Archive"), "the archive from the menu")
            device.pressBack()
            waitFor(By.text("Work"), "the chat list again")

            // Search with the keyboard down.
            tap(By.text("Search"))
            SystemClock.sleep(1_500)
            assertFalse(
                "the keyboard came up although asked not to",
                device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")
            )
            tap(By.text("Chats"))
            waitFor(By.text("Work"), "the chat list after search")

            // A double tap replies; then a forward to the same chat is a copy.
            tap(By.desc("More"))
            tap(By.text("Saved Messages"))
            val line = By.text("Color tokens & springs")
            waitFor(line, "Saved Messages")
            repeat(3) {
                if (device.hasObject(By.desc("Cancel reply"))) return@repeat
                device.findObject(line)?.visibleCenter?.let { at ->
                    device.click(at.x, at.y)
                    // Past Compose's shortest double tap, well inside its longest.
                    SystemClock.sleep(90)
                    device.click(at.x, at.y)
                }
                device.wait(Until.hasObject(By.desc("Cancel reply")), SHORT_WAIT)
            }
            waitFor(By.desc("Cancel reply"), "the reply a double tap starts")
            screenshot("33c-geeks-double-tap-reply")
            tap(By.desc("Cancel reply"))

            repeat(3) {
                if (device.hasObject(By.text("Forward"))) return@repeat
                try {
                    device.findObject(line)?.longClick()
                } catch (_: StaleObjectException) {
                }
                device.wait(Until.hasObject(By.text("Forward")), SHORT_WAIT)
            }
            tap(By.text("Forward"))
            waitFor(By.text("Forward to…"), "the forward sheet")
            // The sheet's first row, in view without scrolling — Saved
            // Messages, further down, was not, and its name was the title
            // behind the sheet.
            tap(By.text("Material Design"))
            // The app goes on to the chat it went to (1.7), where the copy is
            // the newest line.
            waitFor(line, "the forwarded copy in Material Design")
            waitFor(By.textContains("ButtonGroup"), "Material Design, opened by the forward")
            screenshot("33d-geeks-forward-copy")
            assertFalse(
                "a copy was marked as forwarded",
                device.hasObject(By.text("Forwarded from You"))
            )
            device.pressBack()
        } finally {
            geeks.update {
                it.copy(
                    hideStories = false,
                    hideAllChatsTab = false,
                    searchWithoutKeyboard = false,
                    doubleTap = com.telegramyou.app.settings.DoubleTapAction.Nothing,
                    forwardWithoutQuote = false
                )
            }
        }
    }

    /**
     * A round video message (1.7): the camera button held, the circle up
     * with the front camera in it while the finger stays down, and the
     * message in the chat once it lifts. The emulator's front camera is its
     * emulated one — see the AVD in the UI workflow.
     */
    @Test
    fun aVideoMessageIsRecordedByHoldingTheCamera() {
        signIn()
        waitFor(By.text("Design Circle"), "the chat list")
        awaitNoHeadsUp()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        device.executeShellCommand("pm grant $app android.permission.CAMERA")
        device.executeShellCommand("pm grant $app android.permission.RECORD_AUDIO")
        tap(By.text("Design Circle"))
        val camera = By.desc("Camera, hold for a video message")
        waitFor(camera, "the composer's camera button")
        val at = device.findObject(camera).visibleCenter
        var circleUp = false
        holdAt(at.x, at.y, VIDEO_NOTE_HOLD_MS) {
            circleUp = device.hasObject(By.desc("Recording a video message"))
            screenshot("86-video-note-recording")
        }
        assertTrue("no recording circle while the camera was held", circleUp)
        waitFor(By.descStartsWith("Video message, 0:0"), "the video message in the chat")
        screenshot("86b-video-note-sent")

        // Slid up while held, it locks: the finger lifts and the recording
        // goes on, with Delete, Switch camera and Send on the circle. The
        // emulator has both cameras, so the switch is there to turn it.
        val sent = device.findObjects(By.descStartsWith("Video message, 0:0")).size
        holdAndSlide(at.x, at.y, dy = -VIDEO_NOTE_LOCK_SLIDE_PX)
        waitFor(By.desc("Send video message"), "the locked recording's Send")
        SystemClock.sleep(2_000)
        screenshot("86c-video-note-locked")
        tap(By.desc("Switch camera"))
        SystemClock.sleep(1_500)
        screenshot("86d-video-note-switched")
        tap(By.desc("Send video message"))
        val second = SystemClock.uptimeMillis() + STEP_TIMEOUT
        while (SystemClock.uptimeMillis() < second &&
            device.findObjects(By.descStartsWith("Video message, 0:0")).size <= sent
        ) {
            SystemClock.sleep(300)
        }
        assertTrue(
            "the locked recording was not sent",
            device.findObjects(By.descStartsWith("Video message, 0:0")).size > sent
        )
        device.pressBack()
    }

    /** Down at [x], [y], held past a long press, slid by [dy], and lifted. */
    private fun holdAndSlide(x: Int, y: Int, dy: Int) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downAt = SystemClock.uptimeMillis()
        fun touch(action: Int, at: Long, atY: Int) = android.view.MotionEvent.obtain(
            downAt, at, action, x.toFloat(), atY.toFloat(), 0
        ).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
        automation.injectInputEvent(touch(android.view.MotionEvent.ACTION_DOWN, downAt, y), true)
        SystemClock.sleep(1_500)
        val steps = 15
        for (step in 1..steps) {
            automation.injectInputEvent(
                touch(android.view.MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(), y + dy * step / steps),
                true
            )
            SystemClock.sleep(20)
        }
        automation.injectInputEvent(touch(android.view.MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), y + dy), true)
    }

    /**
     * A finger held down at [x], [y] for [millis], as UiDevice cannot: its
     * long click lifts by itself, and a swipe in place cannot be looked at
     * while it lasts. [during] runs once, halfway through the hold.
     */
    private fun holdAt(x: Int, y: Int, millis: Long, during: () -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downAt = SystemClock.uptimeMillis()
        fun touch(action: Int, at: Long) = android.view.MotionEvent.obtain(
            downAt, at, action, x.toFloat(), y.toFloat(), 0
        ).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
        automation.injectInputEvent(touch(android.view.MotionEvent.ACTION_DOWN, downAt), true)
        var looked = false
        while (SystemClock.uptimeMillis() < downAt + millis) {
            if (!looked && SystemClock.uptimeMillis() > downAt + millis / 2) {
                looked = true
                during()
            }
            SystemClock.sleep(50)
        }
        automation.injectInputEvent(touch(android.view.MotionEvent.ACTION_UP, SystemClock.uptimeMillis()), true)
    }

    /**
     * A poll answered with one tap, and a bot's two kinds of buttons: one
     * under its message that asks the bot and shows its answer, and a key of
     * its keyboard under the composer that sends a message it replies to.
     * The demo seeds both, in the chats below the fold, so the list scrolls.
     */
    @Test
    fun aPollIsAnsweredAndABotAnswersItsButtons() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        scrollChatsTo(By.text("Kotlin Night"))
        tap(By.text("Kotlin Night"))
        waitFor(By.text("What do you reach for first?"), "the poll")
        tap(By.text("SharedTransitionLayout"))
        // The vote counted: one more voter, and it can be taken back.
        waitFor(By.text("31 votes"), "the poll's results")
        screenshot("34-poll")

        device.pressBack()
        waitFor(By.text("Kotlin Night"), "the chat list again")
        scrollChatsTo(By.text("Build Bot"))
        tap(By.text("Build Bot"))
        waitFor(By.text("Changelog"), "the bot's buttons")
        tap(By.text("Changelog"))
        waitFor(By.text("Polls and bot buttons landed"), "the bot's answer")
        tap(By.text("Status"))
        waitFor(By.text("All green ✅"), "the bot's reply to a key")
        screenshot("35-bot")
    }

    /**
     * Search as a section of its own: with nothing typed, the people written
     * to most and the chats found before; with a word, the account's chats
     * and public ones apart, and posts from public channels on request —
     * one of which opens its channel.
     */
    @Test
    fun searchFindsChatsAndPublicPosts() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Search"))
        waitFor(By.text("People"), "the people written to most")
        waitFor(By.text("Recent"), "the chats found before")
        // Ready to type from the first moment: the field has the caret.
        waitFor(By.clazz("android.widget.EditText").focused(true), "the search field, focused")
        screenshot("36-search")
        type("Expressive")
        waitFor(By.text("Global search"), "public chats")
        waitFor(By.text("Expressive Design Weekly"), "a public channel")
        tap(By.text("Posts"))
        tap(By.text("Search posts"))
        waitFor(By.textContains("Shape morphing"), "a public post")
        screenshot("37-post-search")
        tap(By.textContains("Shape morphing"))
        waitFor(By.textContains("springs, not curves"), "the channel the post is in")
    }

    /**
     * The messages pack: a poll written in the group's form and sent, a
     * message scheduled from the held send button and then sent from the
     * scheduled list, and a music file in Saved Messages.
     */
    @Test
    fun aPollIsWrittenAndAMessageScheduled() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")

        tap(By.desc("Attach"))
        // The sheet asks for the photos it shows recent ones from; which test
        // meets that request first depends on how the runner shards them.
        allowPhotos()
        tap(By.text("Poll"))
        waitFor(By.text("New poll"), "the poll form")
        // By name: the chat's own field is behind the dialog, and a search
        // by class alone finds it first.
        listOf("Poll question" to "Tea or coffee?", "Answer 1" to "Tea", "Answer 2" to "Coffee").forEach { (field, text) ->
            typeInto(field, text)
        }
        screenshot("38-new-poll")
        tap(By.text("Send"))
        waitFor(By.text("Tea or coffee?"), "the poll in the group")

        type("See you at the review")
        val send = By.desc("Send")
        waitFor(send, "the send button")
        device.findObject(send).longClick()
        tap(By.text("Schedule message"))
        tap(By.text("Next"))
        tap(By.text("Schedule"))
        // The clock in the header, not the snackbar: the snackbar sits
        // behind the keyboard and is gone in four seconds, and the clock is
        // what says the chat now has something waiting.
        waitFor(By.desc("Scheduled messages"), "the clock for what is scheduled")
        tap(By.desc("Scheduled messages"))
        waitFor(By.text("See you at the review"), "the message in the scheduled list")
        screenshot("39-scheduled")
        tap(By.desc("Send now"))
        waitFor(By.text("Nothing is waiting to be sent"), "the list emptied")
        device.pressBack()
        waitFor(By.text("See you at the review"), "the message sent into the group")

        // The composer still has the keyboard up from typing, and the
        // first back only puts it away; press until the list is there.
        repeat(3) {
            if (device.hasObject(By.text("Saved Messages"))) return@repeat
            device.pressBack()
            device.wait(Until.hasObject(By.text("Saved Messages")), 2_000)
        }
        waitFor(By.text("Saved Messages"), "the chat list again")
        tap(By.text("Saved Messages"))
        waitFor(By.text("Expressive Motion"), "the music file")
        // It plays, and while it plays it has a position to drag.
        tap(By.desc("Play Expressive Motion"))
        waitFor(By.desc("Position in Expressive Motion"), "the music playing")
        screenshot("40-audio")
        tap(By.desc("Pause"))
    }

    /**
     * The profile tab: the photo, the name and the three actions; Edit opens
     * the form and a saved bio comes back on the page; the QR code offers to
     * share the demo account's link.
     */
    @Test
    fun theProfileEditsAndSharesItself() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Profile"))
        waitFor(By.text("Set photo"), "the profile's actions")
        waitFor(By.text("@telegramyou"), "the username")
        screenshot("42-profile")

        tap(By.text("Edit"))
        waitFor(By.text("Edit profile"), "the profile form")
        typeInto("Bio", "Built with M3 Expressive")
        tap(By.text("Save"))
        waitFor(By.text("Built with M3 Expressive"), "the saved bio on the profile")

        tap(By.desc("QR code"))
        waitFor(By.text("Share QR code"), "the QR code")
        screenshot("43-profile-qr")
    }

    /**
     * Whether [selector] leaves the screen, looking at a fresh tree each time.
     *
     * UiAutomator reads through the accessibility cache, and after the folder
     * pager changed page the cache went on holding the old page's rows — the
     * dumps showed Work's chats while the screenshot showed News — until
     * something else on screen changed. Clearing it before each look tells
     * the two apart: if the rows still come back, the app is what reports
     * them, and that would be a bug for TalkBack as well.
     */
    private fun goneAfterFreshLooks(selector: BySelector, timeout: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        do {
            if (Build.VERSION.SDK_INT >= 34) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            if (!device.hasObject(selector)) return true
            SystemClock.sleep(500)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    /**
     * A draft: typed, left behind, marked in the chat list, and back in the
     * field on returning — what Telegram keeps on the server for every chat.
     */
    @Test
    fun aDraftStaysWithItsChat() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")
        type("Half a thought")
        // Back until the list: the first press only puts the keyboard away.
        repeat(3) {
            if (device.hasObject(By.textContains("Draft:"))) return@repeat
            device.pressBack()
            device.wait(Until.hasObject(By.textContains("Draft:")), SHORT_WAIT)
        }
        waitFor(By.textContains("Draft: Half a thought"), "the draft in the chat list")
        screenshot("45-draft")
        tap(By.text(GROUP_CHAT))
        waitFor(By.text("Half a thought"), "the draft back in the field")
    }

    /**
     * A video sticker plays: its description appears only once a frame has
     * come out of the two VP9 decoders, so waiting for it is waiting for the
     * decoding to have worked on this device.
     */
    @Test
    fun aVideoStickerPlays() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        // By the chat's preview, not its name: "Artem" is also a story in
        // the rail above, and a tap there opened his story instead.
        scrollChatsTo(By.text("Send me the apk?"))
        tap(By.text("Send me the apk?"))
        waitFor(By.desc("😊 sticker"), "the video sticker's first frame")
        SystemClock.sleep(700)
        screenshot("46-video-sticker")
    }

    @Test
    fun formattingForwardsAndPins() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        scrollChatsTo(By.text("Kotlin Night"))
        tap(By.text("Kotlin Night"))
        waitFor(By.text("What do you reach for first?"), "the group")
        scrollBackTo(By.text("Forwarded from Android Developers"), "the forwarded message")
        screenshot("44-formatting")

        type("**bold** move")
        tap(By.desc("Send"))
        val sent = By.text("bold move")
        waitFor(sent, "the message, formatted and without its markers")
        repeat(3) {
            if (device.hasObject(By.text("Pin"))) return@repeat
            try {
                device.findObject(sent)?.longClick()
            } catch (_: StaleObjectException) {
            }
            device.wait(Until.hasObject(By.text("Pin")), SHORT_WAIT)
        }
        tap(By.text("Pin"))
        repeat(3) {
            if (device.hasObject(By.text("Unpin"))) return@repeat
            try {
                device.findObjects(sent).lastOrNull()?.longClick()
            } catch (_: StaleObjectException) {
            }
            device.wait(Until.hasObject(By.text("Unpin")), SHORT_WAIT)
        }
        waitFor(By.text("Unpin"), "the menu of a pinned message")
        // The quick reactions sit over the menu, with the arrow to the rest.
        waitFor(By.desc("More reactions"), "the reaction row over the menu")
        screenshot("44a-message-menu")
        // Forward is on the message's own menu, not only behind a selection.
        tap(By.text("Forward"))
        waitFor(By.text("Forward to…"), "the forward sheet for one message")
        screenshot("44b-forward-one")
        device.pressBack()
    }

    /**
     * The music library, switched on under For geeks and reached from My
     * music: its front page, an album — four tracks posted together — and
     * that album played from its first track as posted.
     */
    @Test
    fun theMusicLibraryPlaysAnAlbum() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        scrollSettingsTo(By.text("For geeks"))
        tap(By.text("For geeks"))
        scrollDownTo(By.text("Music library"))
        tap(By.text("Music library"))
        device.pressBack()
        waitFor(By.text("For geeks"), "the settings again")
        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list again")
        // With the library on, it is a tab of its own, and Saved Messages —
        // where a person keeps their music — leads its front page.
        tap(By.text("Music"))
        waitFor(By.text("Saved Messages"), "Saved Messages first in the library")
        waitFor(By.text("Just arrived"), "the library's front page")
        screenshot("84-library")
        // Scrolled, for how the large title folds; then each page, for how
        // they look — pictures for a person to judge, not assertions.
        dragList(0.75, 0.3)
        screenshot("84b-library-scrolled")
        dragList(0.3, 0.75)
        tap(By.text("Playlists"))
        waitFor(By.text("Saved Messages"), "the playlists")
        screenshot("84c-library-playlists")
        tap(By.text("Artists"))
        waitFor(By.text("Shape Shifters"), "the artists")
        screenshot("84d-library-artists")
        tap(By.text("Tracks"))
        waitFor(By.text("Morning Light"), "the tracks")
        screenshot("84e-library-tracks")
        tap(By.text("Albums"))
        tap(By.text("Shape Shifters"))
        waitFor(By.textStartsWith("Album · 4 tracks"), "the album's page")
        screenshot("85-album")
        tap(By.text("Play"))
        waitFor(By.desc("Now playing: Wavy Line"), "the album from its first track")
        // Back on the tab, with the mini player at its foot.
        device.pressBack()
        waitFor(By.text("Albums"), "the library's tabs again")
        screenshot("85b-library-playing")
        // Scrolled: the tabs take the bar's scrolled colour with it, and the
        // page runs on under the mini player rather than stopping above it
        // (1.6.9). Pictures for a person to judge, not assertions.
        dragList(0.75, 0.3)
        screenshot("85c-library-playing-scrolled")
        // The chats run on under it too, with no band of background round it.
        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list with the mini player")
        dragList(0.75, 0.45)
        screenshot("85d-chats-playing")
        // With the mini player at the top, it takes the head's colour as well.
        setMiniPlayerOnTop()
        tap(By.text("Music"))
        waitFor(By.text("Albums"), "the library's tabs with the mini player on top")
        dragList(0.75, 0.3)
        screenshot("85e-library-player-on-top")

        // Off again: the tests share one install, and with the library on
        // "My music" opens the library instead of the list another test
        // expects. Stopped first, so no music runs into the next test — if
        // the album has not already played out on a slow emulator.
        if (device.hasObject(By.desc("Stop music"))) tap(By.desc("Stop music"))
        setMiniPlayerOnTop()
        scrollSettingsTo(By.text("For geeks"))
        tap(By.text("For geeks"))
        scrollDownTo(By.text("Music library"))
        tap(By.text("Music library"))
        device.pressBack()
        waitFor(By.text("For geeks"), "the settings again")
    }

    /**
     * Voice messages as a run and as the app's: two of Lina's in a row play
     * one after the other, the speed changes from the bar, and the bar — and
     * the voice — stay when the chat is left, until stopped there.
     */
    @Test
    fun voiceMessagesPlayInARowAndOutliveTheChat() {
        signIn()
        waitFor(By.text("Lina Park"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        scrollBackTo(By.desc("Play voice message"), "Lina's voice messages")
        // Scrolling up, the second of the two comes into view first; both
        // on screen, and the upper one tapped, so there is one to follow.
        repeat(4) {
            if (device.findObjects(By.desc("Play voice message")).size >= 2) return@repeat
            dragList(0.4, 0.6)
        }
        tapTopmost(By.desc("Play voice message"))
        waitFor(By.text("Voice message · 1 more"), "the bar saying another follows")
        tap(By.desc("Voice speed 1×"))
        waitFor(By.desc("Voice speed 1.5×"), "the speed changed from the bar")
        screenshot("83-voice-bar")
        // The first ends by itself — twelve seconds at 1.5× — and the
        // second follows with nothing more to come.
        assertTrue(
            "the second voice message did not follow the first",
            device.wait(Until.hasObject(By.text("Voice message")), 30_000)
        )
        // Paused from the bar, so it is still there to be seen from the chat
        // list: eight seconds at 1.5× can run out on the way back to it.
        tap(By.desc("Pause the voice bar"))
        waitFor(By.desc("Resume the voice bar"), "the voice message paused")
        backTo(By.text("Material Design"), "the chat list")
        waitFor(By.descStartsWith("Voice message from"), "the bar over the chat list")
        tap(By.desc("Stop voice message"))
        assertTrue(
            "the bar stayed after Stop",
            device.wait(Until.gone(By.descStartsWith("Voice message from")), STEP_TIMEOUT)
        )
    }

    /**
     * The download manager, from the chat list's menu: a finished file and a
     * paused one, as the demo seeds them; the paused one resumed and paused
     * again from its row, the whole queue from the bar, and a finished file
     * taken off the list without being deleted.
     */
    @Test
    fun downloadsArePausedResumedAndCleared() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.desc("More"))
        tap(By.text("Downloads"))
        waitFor(By.text("Expressive-guidelines.pdf"), "a paused download")
        waitFor(By.textStartsWith("Paused"), "the paused one saying so")
        waitFor(By.text("release-notes.txt"), "a finished download")
        screenshot("82-downloads")

        tap(By.desc("Resume Expressive-guidelines.pdf"))
        waitFor(By.desc("Pause Expressive-guidelines.pdf"), "the download running again")
        tap(By.desc("Pause Expressive-guidelines.pdf"))
        waitFor(By.desc("Resume Expressive-guidelines.pdf"), "the download paused again")
        // Everything paused: the bar offers to resume the lot, then to pause it.
        tap(By.desc("Resume all"))
        waitFor(By.desc("Pause all"), "the queue running")
        tap(By.desc("Pause all"))
        waitFor(By.desc("Resume all"), "the queue paused")

        tap(By.desc("More for release-notes.txt"))
        waitFor(By.text("Delete from phone"), "deleting offered apart from removing")
        tap(By.text("Remove from list"))
        waitFor(By.textStartsWith("Taken off the list"), "said to be still on the phone")
        assertTrue(
            "the finished file stayed on the list",
            device.wait(Until.gone(By.text("release-notes.txt")), STEP_TIMEOUT)
        )
    }

    /**
     * Listening through a music channel: a track tapped plays in the app's
     * player; the mini player follows; the full player opens from it; its
     * queue is the channel's whole music and another track starts from it
     * without the list moving; and Stop takes the mini player away.
     */
    @Test
    fun musicPlaysThroughAChannel() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        scrollChatsTo(By.text(MUSIC_CHANNEL))
        tap(By.text(MUSIC_CHANNEL))
        waitFor(By.desc("Play Morning Light"), "the newest track")
        tap(By.desc("Play Morning Light"))
        waitFor(By.desc("Now playing: Morning Light"), "the mini player")
        screenshot("77-mini-player")
        // And the system's own player, in the shade — which 1.6.3 to 1.6.5
        // never had, because the session was not the service's (see
        // PlaybackService). Looked for in System UI only: the app's own
        // mini player says the same under the shade. By the channel's
        // performers rather than one title: a twelve-second track can end
        // while the shade comes down, and the next one is as good a proof.
        device.openNotification()
        assertTrue(
            "the music is not in the shade's player",
            device.wait(
                Until.hasObject(By.pkg(SYSTEM_UI).text(Pattern.compile(".*(Tonal Collective|Shape Shifters|Material Sound).*"))),
                STEP_TIMEOUT
            )
        )
        screenshot("77b-shade-player")
        device.pressBack()
        device.waitForIdle(IDLE_TIMEOUT)

        // Whatever is playing by now: a twelve-second track can have ended
        // and handed over to the next while the shade was open.
        tap(By.descStartsWith("Now playing"))
        waitFor(By.text("Sleep timer"), "the full player")
        waitFor(By.desc("Pause"), "the track playing")
        // Each track round and round from here, before one is picked from
        // the queue: the demo's are twelve seconds long, and the one picked
        // is saved and looked for again later, by which time it would have
        // ended and handed over. Set this late, it once caught the queue
        // passing through Repeat all and wrapping round to another track.
        tap(By.desc("Repeat off"))
        tap(By.desc("Repeat all"))
        waitFor(By.desc("Repeat one"), "the track on repeat")
        screenshot("78-player")

        // Share hands the track to Android's share sheet (1.7): the app
        // leaves the foreground for it, and Back returns to the player.
        tap(By.desc("More"))
        tap(By.text("Share"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val sheetBy = SystemClock.uptimeMillis() + STEP_TIMEOUT
        while (SystemClock.uptimeMillis() < sheetBy && device.currentPackageName == app) {
            SystemClock.sleep(200)
        }
        screenshot("78c-player-share")
        assertTrue("Share opened nothing", device.currentPackageName != app)
        device.pressBack()
        waitFor(By.text("Queue"), "the player after sharing")

        tap(By.text("Queue"))
        waitFor(By.textContains("12 tracks"), "the channel's whole music in the queue")
        // The oldest track is at the far end of the queue: found by its
        // search rather than by scrolling to it.
        type("Tonal")
        waitFor(By.text("Tonal Spot"), "the queue searched")
        tap(By.text("Tonal Spot"))
        // The queue stays open; the new track is the one playing.
        waitFor(By.desc("Playing"), "the new track marked playing in the queue")
        screenshot("79-queue")
        // Back past the keyboard, then the sheet.
        repeat(3) {
            if (!device.hasObject(By.textContains("12 tracks"))) return@repeat
            device.pressBack()
            SystemClock.sleep(600)
        }
        waitFor(By.text("Tonal Spot"), "the player on the new track")

        // Three ways through the queue, from one button's menu.
        tap(By.desc("Order: In order"))
        waitFor(By.text("Reversed"), "the order menu")
        screenshot("78b-order")
        tap(By.text("Reversed"))
        waitFor(By.desc("Order: Reversed"), "the queue reversed")

        // Kept in Saved Messages, which then plays as the queue — the same
        // track, carrying on — and a sleep timer for the end of the track.
        tap(By.desc("More"))
        tap(By.text("Save to Saved Messages"))
        waitFor(By.text("Saved to Saved Messages"), "the track saved")
        tap(By.text("Play saved"))
        waitFor(By.text("Saved Messages"), "the player on Saved Messages' music")
        waitFor(By.text("Tonal Spot"), "the saved track playing")
        // Fifteen minutes rather than the end of the track: the demo's track
        // is twelve seconds long, and by now it can have ended, taking an
        // end-of-track timer with it before the chip could be seen.
        tap(By.text("Sleep timer"))
        tap(By.text("15 minutes"))
        waitFor(By.text("Sleep timer: 15 minutes"), "the sleep timer set")

        // Closed by pulling it down from the cover, not by the arrow at the
        // top: the reach a one-handed phone asked for.
        dragList(0.3, 0.8)
        waitFor(By.desc("Now playing: Tonal Spot"), "the mini player on the new track")
        // The cross stops it. A sideways swipe did too in 1.6.8, and went
        // for missing more often than it landed on a phone.
        tap(By.desc("Stop music"))
        // Looked for fresh, as waitFor looks: through UiAutomator's cache
        // a mini player that had changed stayed "there" for twenty seconds.
        val stopBy = SystemClock.uptimeMillis() + STEP_TIMEOUT
        var gone = false
        while (!gone && SystemClock.uptimeMillis() < stopBy) {
            if (Build.VERSION.SDK_INT >= 34) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            gone = !device.hasObject(By.descStartsWith("Now playing"))
            if (!gone) SystemClock.sleep(300)
        }
        if (!gone) screenshot("failed-stopping-the-mini-player")
        assertTrue("the mini player stayed after Stop", gone)

        // Every chat's music in one place, from the chat list's menu. The
        // bottom bar's Chats, not a chat's name: the list comes back scrolled
        // to the channel, low down, with the top chats out of view.
        backTo(By.text("Chats"), "the chat list")
        // The list comes back scrolled to where the channel was, low down,
        // with the collapsing header — and its menu — scrolled away above.
        repeat(4) {
            if (device.wait(Until.hasObject(By.desc("More")), SHORT_WAIT)) return@repeat
            try {
                device.findObjects(By.scrollable(true))
                    .maxByOrNull { it.visibleBounds.height() }
                    ?.scroll(Direction.UP, 0.8f)
            } catch (_: StaleObjectException) {
            }
        }
        tap(By.desc("More"))
        tap(By.text("My music"))
        waitFor(By.text("Morning Light"), "the channel's tracks in My music")
        waitFor(By.textContains("from Material Sound"), "where each came from")
        screenshot("80-my-music")
        tap(By.desc("Play Morning Light"))
        waitFor(By.desc("Now playing: Morning Light"), "the mini player over My music")

        // And from search, on its Music tab.
        device.pressBack()
        tap(By.text("Search"))
        waitFor(By.clazz("android.widget.EditText").focused(true), "the search field")
        type("Tonal")
        // The fifth tab: past the edge on a phone until the row scrolls.
        repeat(4) {
            if (device.wait(Until.hasObject(By.text("Music")), SHORT_WAIT)) return@repeat
            try {
                device.findObjects(By.scrollable(true))
                    .minByOrNull { it.visibleBounds.height() }
                    ?.scroll(Direction.RIGHT, 0.8f)
            } catch (_: StaleObjectException) {
            }
        }
        tap(By.text("Music"))
        waitFor(By.textContains("from Material Sound"), "a track found in every chat's music")
        screenshot("81-search-music")
        tap(By.desc("Play Tonal Spot"))
        // The mini player is at the foot of the page since 1.6.7, under the
        // keyboard while there is one: the keyboard goes down first. Back
        // only while it is up — without it, Back clears the query.
        if (device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) {
            device.pressBack()
        }
        waitFor(By.desc("Now playing: Tonal Spot"), "the mini player under search")
    }

    /**
     * A chat's shared media, a tab per kind: its files, one of them opened in
     * whatever the phone reads it with; its links; and a tab with nothing in
     * it saying so rather than spinning.
     */
    @Test
    fun sharedMediaHasATabPerKind() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")
        awaitNoHeadsUp()
        tapTopmost(By.text(GROUP_CHAT))
        waitFor(By.text("Info"), "the info screen")
        scrollDownTo(By.text("Shared media"))
        tap(By.text("Shared media"))
        waitFor(By.text("Files"), "the tabs")

        tap(By.text("Files"))
        waitFor(By.text("Expressive-guidelines.pdf"), "the group's files")
        screenshot("76-shared-files")
        // Opened in another app — or, on an emulator with nothing that reads
        // text, said so. Either way, not a crash, and back to the tab.
        tap(By.text("release-notes.txt"))
        SystemClock.sleep(2_500)
        screenshot("76b-file-opened")
        backTo(By.text("Expressive-guidelines.pdf"), "the files tab again")

        tap(By.text("Music"))
        waitFor(By.text("No music here yet"), "an empty tab saying so")

        // Past the tabs a phone's width shows, by swiping the pages as a
        // person would: Music, Voice, Links.
        repeat(2) {
            device.swipe(
                (device.displayWidth * 0.85).toInt(), device.displayHeight / 2,
                (device.displayWidth * 0.15).toInt(), device.displayHeight / 2,
                12
            )
            device.waitForIdle(IDLE_TIMEOUT)
        }
        waitFor(By.text("m3.material.io"), "the link Noor sent")
        screenshot("76c-shared-links")
    }

    /**
     * Running a group as its owner: a member made admin from their menu,
     * what members may do switched on the permissions screen, and a link
     * made that lasts a day and lets ten in.
     */
    @Test
    fun aGroupIsRunByItsOwner() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")
        awaitNoHeadsUp()
        tapTopmost(By.text(GROUP_CHAT))
        waitFor(By.text("Info"), "the info screen")
        scrollDownTo(By.text("Members"))
        // Nadia is an admin, and says so by her title.
        scrollDownTo(By.text("Motion"))
        scrollDownTo(By.desc("Manage Pavel Gromov"))
        tap(By.desc("Manage Pavel Gromov"))
        tap(By.text("Make admin"))
        // His rights first, in a sheet: one more switched on, then saved.
        waitFor(By.text("Make Pavel Gromov an admin"), "the admin rights sheet")
        tap(By.text("Add new admins"))
        screenshot("70b-admin-rights")
        tap(By.text("Make admin"))
        waitFor(By.text("Pavel Gromov is an admin now"), "Pavel made an admin")
        screenshot("70-group-members")

        // The members found by name, the rest out of the list meanwhile.
        // The keyboard comes up over the results, so it goes first.
        type("orl")
        device.pressBack()
        scrollDownTo(By.text("Nadia Orlova"))
        assertTrue(
            "Pavel still listed while searching for Nadia",
            device.wait(Until.gone(By.text("Pavel Gromov")), SHORT_WAIT)
        )
        device.findObject(By.clazz("android.widget.EditText"))?.text = ""
        device.waitForIdle(IDLE_TIMEOUT)

        // Two people asked to join through a link that asks first.
        scrollBackTo(By.text("Join requests"), "the join requests row")
        tap(By.text("Join requests"))
        waitFor(By.text("Ilya Brand"), "Ilya's request")
        screenshot("72b-join-requests")
        tap(By.text("Add to group"))
        waitFor(By.textEndsWith("joined the group"), "somebody let in")
        backTo(By.text("Info"), "the info screen again")

        scrollBackTo(By.text("Permissions"), "the permissions row")
        tap(By.text("Permissions"))
        waitFor(By.text("What members can do"), "the permissions screen")
        tap(By.text("Send polls"))
        screenshot("71-group-permissions")
        backTo(By.text("Info"), "the info screen again")

        scrollBackTo(By.text("Invite links"), "the invite links row")
        tap(By.text("Invite links"))
        waitFor(By.text("Design review"), "the group's links")
        tap(By.desc("New link"))
        waitFor(By.text("New invite link"), "the new link dialog")
        tap(By.text("1 day"))
        tap(By.text("10"))
        tap(By.text("Create"))
        waitFor(By.text("Link created"), "the link made")
        screenshot("72-invite-links")
    }

    /**
     * A forum opens onto its topics, a topic onto its own messages and not
     * the others', what is written there stays there, and a topic can be
     * started.
     */
    @Test
    fun aForumOpensOnItsTopics() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        scrollChatsTo(By.text(FORUM_CHAT))
        tap(By.text(FORUM_CHAT))
        waitFor(By.desc("New topic"), "the forum's topics")
        waitFor(By.text("General"), "General among them")
        screenshot("73-forum-topics")

        tap(By.text("Releases"))
        // Not the topic's newest line, which the topic list shows too.
        waitFor(By.textContains("custom emoji and sending a location"), "the topic's messages")
        assertNull(
            "another topic's message in this one",
            device.findObject(By.textContains("keyboard opens on Android 12"))
        )
        type("Screenshots next")
        tap(By.desc("Send"))
        waitFor(By.text("Screenshots next"), "the message sent into the topic")
        screenshot("74-forum-topic")
        // Half a thought left in the field is the topic's draft, and the
        // topic list says so. Past the second the draft waits to be kept.
        type("Half a thought")
        SystemClock.sleep(1_800)

        backTo(By.desc("New topic"), "the topics again")
        waitFor(By.text("Draft: Half a thought"), "the topic's draft in the list")
        tap(By.desc("New topic"))
        waitFor(By.text("Topic name"), "the new topic dialog")
        type("Screenshots")
        tap(By.text("Create"))
        waitFor(By.text("Screenshots"), "the new topic in the list")

        // Its admin can close it from its menu.
        tap(By.desc("Manage Screenshots"))
        tap(By.text("Close topic"))
        waitFor(By.text("Screenshots is closed"), "the topic closed")
        waitFor(By.desc("Closed"), "the closed mark on it")
    }

    /**
     * Other people. A group member tapped opens their profile; a private
     * chat's info says who the person is — number, username, bio — and
     * blocks them; Settings → Privacy → Blocked users lists them and gives
     * them back. Unblocked at the end, so no later test in this process
     * meets a blocked Lina.
     */
    @Test
    fun peopleHaveProfilesAndCanBeBlocked() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")
        awaitNoHeadsUp()
        tapTopmost(By.text(GROUP_CHAT))
        waitFor(By.text("Info"), "the info screen")
        scrollDownTo(By.text("Members"))
        scrollDownTo(By.text("Nadia Orlova"))
        tap(By.text("Nadia Orlova"))
        waitFor(By.text("Send message"), "Nadia's profile")
        waitFor(By.text("Motion and springs"), "her bio")
        // And the id Telegram knows her by, last, as the forks show it.
        scrollDownTo(By.text("ID"))
        screenshot("47-person")

        backTo(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        waitFor(By.desc("GIF"), "Lina's chat")
        awaitNoHeadsUp()
        // The header, not one of the "Lina Park" labels over her messages:
        // the topmost of them.
        tapTopmost(By.text("Lina Park"))
        waitFor(By.text("Mobile"), "Lina's number")
        waitFor(By.textContains("Swims at dawn"), "her bio")
        screenshot("48-private-info")
        scrollDownTo(By.text("Block user"))
        tap(By.text("Block user"))
        waitFor(By.text("Block Lina?"), "the block question")
        tap(By.text("Block"))
        waitFor(By.text("Unblock user"), "Lina blocked")

        backTo(By.text("Material Design"), "the chat list")
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        scrollSettingsTo(By.text("Privacy"))
        tap(By.text("Privacy"))
        waitFor(By.text("Phone number"), "the privacy rules")
        scrollDownTo(By.text("Blocked users"))
        tap(By.text("Blocked users"))
        waitFor(By.text("Lina Park"), "Lina in the block list")
        screenshot("49-blocked")
        tap(By.text("Unblock"))
        waitFor(By.text("Nobody is blocked"), "the list empty again")
    }

    /**
     * Contacts, from the chat list's menu: one added by number, whose chat
     * opens at once — and that chat then cleared and deleted from the list's
     * long-press menu. A chat of its own making, so no other test's chat
     * is taken away from it.
     */
    @Test
    fun aContactIsAddedAndTheirChatClearedAndDeleted() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.desc("More"))
        tap(By.text("Contacts"))
        waitFor(By.text("Lina Park"), "the contacts")
        screenshot("50-contacts")

        tap(By.desc("Add contact"))
        waitFor(By.text("Phone number"), "the add-contact dialog")
        val fields = By.clazz("android.widget.EditText")
        device.wait(Until.hasObject(fields), STEP_TIMEOUT)
        val (phone, first, last) = device.findObjects(fields)
        phone.text = "+15550109999"
        first.text = "Tess"
        last.text = "Probe"
        device.waitForIdle(IDLE_TIMEOUT)
        waitFor(By.text("Tess"), "the name kept beside the number")
        tap(By.text("Add"))
        waitFor(By.text("Tess Probe"), "the chat with the new contact")
        type("Hello, Tess")
        tap(By.desc("Send"))
        waitFor(By.text("Hello, Tess"), "the message sent")

        backTo(By.text("Material Design"), "the chat list")
        scrollChatsTo(By.text("Tess Probe"))
        longPressChat("Tess Probe", "Clear history")
        tap(By.text("Clear history"))
        waitFor(By.text("Clear history?"), "the clear question")
        tap(By.text("Clear"))
        assertTrue(
            "the cleared chat should lose its last message",
            device.wait(Until.gone(By.textContains("Hello, Tess")), STEP_TIMEOUT)
        )

        longPressChat("Tess Probe", "Delete chat")
        tap(By.text("Delete chat"))
        waitFor(By.text("Delete the chat with Tess?"), "the delete question")
        waitFor(By.text("Also delete for Tess"), "the choice to delete for both")
        screenshot("51-delete-chat")
        tap(By.text("Delete"))
        assertTrue(
            "the deleted chat should leave the list",
            device.wait(Until.gone(By.text("Tess Probe")), STEP_TIMEOUT)
        )
    }

    /**
     * Settings → Appearance: its own screen, with the preview on top. The
     * wallpaper's colour off, an accent chosen, colours from the avatar on,
     * the shapes wallpaper, soft bubbles, two-line previews and less motion
     * — and a chat opened in all that, out of a row that no longer opens
     * out — and everything put back, since
     * these settings outlive the test and would recolour every screenshot
     * after it.
     */
    @Test
    fun appearanceHasItsOwnScreen() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        tap(By.text("Appearance"))
        waitFor(By.desc("Preview"), "the preview")
        waitFor(By.text("Accent colour"), "the accents")
        tap(By.text("Colour from your wallpaper"))
        // The swatches answer only once the wallpaper's colour is off, which
        // the accent row's summary says by naming the accent.
        waitFor(By.text("Teal"), "the accents enabled")
        tap(By.desc("Pink"))
        waitFor(By.text("Pink"), "Pink as the accent")
        SystemClock.sleep(700)
        screenshot("52-appearance")
        scrollDownTo(By.text("Colours from the avatar"))
        tap(By.text("Colours from the avatar"))
        // The four chat backgrounds, one row, the last of them in view.
        scrollDownTo(By.text("Grid"))
        tap(By.text("Dots"))
        screenshot("52c-appearance-chat-background")
        // Below the wallpaper cards, which push it off a phone's screen.
        scrollDownTo(By.text("Soft"))
        tap(By.text("Soft"))
        screenshot("52b-appearance-wallpapers")
        scrollDownTo(By.text("Two-line previews"))
        tap(By.text("Two-line previews"))
        scrollDownTo(By.text("Less motion"))
        tap(By.text("Less motion"))
        // The player's cover, which Less motion holds still: its switch says so.
        // To the summary itself, not the switch's title: the title can be
        // the last line on screen with its summary still below the edge,
        // and out of a lazy list's tree.
        scrollDownTo(By.text("Held still by Less motion"))
        waitFor(By.text("Held still by Less motion"), "the cover switch answering Less motion")

        // Back lands on the Settings tab, and back again would leave the
        // app: the chat list is its own tab.
        device.pressBack()
        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list")
        tap(By.text("Lina Park"))
        waitFor(By.desc("GIF"), "Lina's chat, in her colours")
        screenshot("53-chat-avatar-colours")

        // Put back: each tap undoes one above, in the same order. Not in a
        // finally — a toggle is blind, and undoing steps that never ran
        // would set what the failure left alone.
        backTo(By.text("Material Design"), "the chat list")
        tap(By.text("Settings"))
        tap(By.text("Appearance"))
        waitFor(By.text("Accent colour"), "the accents again")
        tap(By.desc("Teal"))
        waitFor(By.text("Teal"), "Teal again")
        tap(By.text("Colour from your wallpaper"))
        scrollDownTo(By.text("Colours from the avatar"))
        tap(By.text("Colours from the avatar"))
        scrollDownTo(By.text("Plain"))
        tap(By.text("Plain"))
        scrollDownTo(By.text("Accent"))
        tap(By.text("Accent"))
        scrollDownTo(By.text("Two-line previews"))
        tap(By.text("Two-line previews"))
        scrollDownTo(By.text("Less motion"))
        tap(By.text("Less motion"))
        device.pressBack()
        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list")
    }

    /**
     * Settings → Chat folders: a folder made from a name and a type of chat,
     * found in the list with the chats it took in, opened again and
     * deleted — so no later test meets a fourth tab.
     */
    @Test
    fun aFolderIsMadeAndDeleted() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Chat folders"), "the settings")
        tap(By.text("Chat folders"))
        waitFor(By.text("Work"), "the demo's folders")
        tap(By.desc("New folder"))
        waitFor(By.text("New folder"), "the folder editor")
        typeInto("Folder name", "Friends")
        scrollDownTo(By.text("Groups"))
        tap(By.text("Groups"))
        tap(By.text("Save"))
        // Gone first: "Friends" is also in the editor's own name field, and
        // waiting for the text alone found it there and tapped the field.
        assertTrue(
            "the editor did not close after saving",
            device.wait(Until.gone(By.desc("Folder name")), STEP_TIMEOUT)
        )
        waitFor(By.text("Your folders"), "the folder list")
        waitFor(By.text("Friends"), "the new folder in the list")
        screenshot("56-folders")

        tap(By.text("Friends"))
        waitFor(By.text("Edit folder"), "the folder opened again")
        scrollDownTo(By.text("Delete folder"))
        tap(By.text("Delete folder"))
        tap(By.text("Delete"))
        waitFor(By.text("Work"), "the folder list")
        assertTrue(
            "the folder is still listed after deleting it",
            device.wait(Until.gone(By.text("Friends")), STEP_TIMEOUT)
        )
        device.pressBack()
        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list")
    }

    /**
     * The smiley opens emoji, GIFs and stickers in the keyboard's place; the
     * keyboard key that replaces it brings the keyboard back. A GIF sent
     * from the panel arrives in the conversation.
     */
    @Test
    fun emojiGifsAndStickersShareAPanel() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        waitFor(By.desc("Emoji, GIFs and stickers"), "the composer's smiley")
        tap(By.desc("Emoji, GIFs and stickers"))
        // Its own button, not the picker: the picker is an Android View,
        // and the description set on its Compose wrapper is not published.
        waitFor(By.desc("Backspace"), "the emoji tab")
        waitFor(By.desc("Keyboard"), "the keyboard key in the smiley's place")
        screenshot("57-panel-emoji")
        // Pulled taller by its handle, and back.
        tap(By.desc("Expand panel"))
        waitFor(By.desc("Collapse panel"), "the panel taller")
        SystemClock.sleep(700)
        screenshot("57b-panel-expanded")
        tap(By.desc("Collapse panel"))
        waitFor(By.desc("Expand panel"), "the panel back to size")
        SystemClock.sleep(500)

        // The account's custom-emoji set, over the standard ones: its emoji
        // go into the field as their plain ones, and backspace takes one out.
        tap(By.desc("TelegramYou"))
        waitFor(By.desc("🦄 custom emoji"), "the custom emoji set")
        screenshot("57c-panel-custom-emoji")
        tap(By.desc("🦄 custom emoji"))
        waitFor(By.clazz("android.widget.EditText").textContains("🦄"), "the custom emoji in the field")
        tap(By.desc("Backspace"))
        tap(By.desc("Standard emoji"))
        waitFor(By.desc("Backspace"), "the standard emoji again")

        tap(By.text("GIFs"))
        waitFor(By.desc("GIF"), "a GIF in the panel")
        screenshot("58-panel-gifs")
        // Sent, it shows in the conversation — above the panel, where no
        // GIF was before: Lina's own is further up, out of sight.
        val panelTop = device.findObject(By.text("GIFs")).visibleBounds.top
        // The GIFs play, and a node can go stale between being found and
        // being asked where it is; asked again, then.
        var sent = false
        repeat(3) {
            if (sent) return@repeat
            try {
                device.findObjects(By.desc("GIF")).first { it.visibleBounds.top > panelTop }.click()
                sent = true
            } catch (_: StaleObjectException) {
            }
        }
        assertTrue("no GIF in the panel could be tapped", sent)
        val deadline = SystemClock.uptimeMillis() + STEP_TIMEOUT
        var arrived = false
        while (!arrived && SystemClock.uptimeMillis() < deadline) {
            if (Build.VERSION.SDK_INT >= 34) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            arrived = try {
                device.findObjects(By.desc("GIF")).any { it.visibleBounds.bottom <= panelTop }
            } catch (_: StaleObjectException) {
                false
            }
            if (!arrived) SystemClock.sleep(250)
        }
        assertTrue("the GIF never arrived in the chat", arrived)

        tap(By.text("Stickers"))
        waitFor(By.desc("Recent stickers"), "the sticker tab")
        screenshot("59-panel-stickers")

        tap(By.desc("Keyboard"))
        waitFor(By.desc("Emoji, GIFs and stickers"), "the smiley back, with the keyboard")
        backTo(By.text("Material Design"), "the chat list")
    }

    /**
     * A photo opens among the chat's others, and a swipe goes to the next —
     * the album in Kotlin Night, three photos, counted at the top.
     */
    @Test
    fun photosAreASwipeApart() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        scrollChatsTo(By.text("Kotlin Night"))
        tap(By.text("Kotlin Night"))
        waitFor(By.text("What do you reach for first?"), "the group")
        scrollBackTo(By.text("Photos from the meetup"), "the album")
        tapTopmost(By.desc("Photo"))
        // Whichever photo the tap landed on — the album lays its cells out
        // by their shapes, so "topmost" is not always the first.
        waitFor(By.textEndsWith(" of 3"), "the gallery on one of the album's photos")
        screenshot("60-gallery")
        val opened = device.findObject(By.textEndsWith(" of 3")).text.substringBefore(" of").toInt()
        val width = device.displayWidth
        val middle = device.displayHeight / 2
        val next = if (opened < 3) opened + 1 else opened - 1
        if (next > opened) {
            device.swipe((width * 0.85).toInt(), middle, (width * 0.15).toInt(), middle, 12)
        } else {
            device.swipe((width * 0.15).toInt(), middle, (width * 0.85).toInt(), middle, 12)
        }
        waitFor(By.text("$next of 3"), "the neighbouring photo after a swipe")
        device.pressBack()
        waitFor(By.text("Photos from the meetup"), "the chat, the gallery closed")
    }

    /**
     * Settings → Privacy → App lock: a PIN set twice, the app left and
     * opened again to the lock, the PIN typed on its keypad, and the lock
     * turned off again so no later test in this process meets it.
     */
    @Test
    fun theAppLocksBehindAPin() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        scrollSettingsTo(By.text("Privacy"))
        tap(By.text("Privacy"))
        scrollDownTo(By.text("App lock"))
        tap(By.text("App lock"))
        waitFor(By.text("Lock with a PIN"), "the app lock settings")
        tap(By.text("Lock with a PIN"))
        typeInto("PIN", "1234")
        tap(By.text("Next"))
        waitFor(By.text("Repeat the PIN"), "the PIN asked again")
        typeInto("PIN", "1234")
        tap(By.text("Save"))
        waitFor(By.text("Change PIN"), "the lock on")
        screenshot("61-app-lock-settings")

        device.pressHome()
        launchApp()
        waitFor(By.desc("0 of 4 digits"), "the lock screen")
        screenshot("62-app-lock")
        "1234".forEach { digit -> tap(By.desc("Digit $digit")) }
        waitFor(By.text("Material Design"), "the chat list, unlocked")

        tap(By.text("Settings"))
        scrollSettingsTo(By.text("Privacy"))
        tap(By.text("Privacy"))
        scrollDownTo(By.text("App lock"))
        tap(By.text("App lock"))
        waitFor(By.text("Change PIN"), "the lock settings again")
        tap(By.text("Lock with a PIN"))
        assertTrue(
            "the lock did not turn off",
            device.wait(Until.gone(By.text("Change PIN")), STEP_TIMEOUT)
        )
        backTo(By.text("Appearance"), "the settings")
        tap(By.text("Chats"))
        waitFor(By.text("Material Design"), "the chat list")
    }

    /**
     * Selecting messages: Select from one's menu, then a tap on the empty
     * part of another's row — beside the bubble, not on it — adds that one
     * too, and the count says two. The row was not a target before, only
     * the bubble, which the owner found by missing it.
     */
    @Test
    fun messagesAreSelectedByTheirWholeRow() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        val first = By.text("Yes — and the split send button feels great.")
        val second = By.text("Did you try the expressive loading indicator?")
        scrollBackTo(second, "the two messages")
        repeat(3) {
            if (device.hasObject(By.text("Select"))) return@repeat
            try {
                device.findObject(first)?.longClick()
            } catch (_: StaleObjectException) {
            }
            device.wait(Until.hasObject(By.text("Select")), SHORT_WAIT)
        }
        tap(By.text("Select"))
        waitFor(By.desc("Clear selection"), "the selection toolbar")
        // Lina's message sits at the start of its row; the far end of the
        // row, well clear of the bubble, is empty conversation.
        val row = device.findObject(second).visibleBounds.centerY()
        device.click((device.displayWidth * 0.94).toInt(), row)
        waitFor(By.text("2"), "two selected")
        SystemClock.sleep(600)
        screenshot("65-selection")
        tap(By.desc("Clear selection"))
        waitFor(By.text("Message"), "the composer back")
        backTo(By.text("Material Design"), "the chat list")
    }

    /** A GIF in a message kept among the saved ones, from its menu. */
    @Test
    fun aGifIsAddedToGifs() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        scrollBackTo(By.desc("GIF"), "the GIF")
        repeat(3) {
            if (device.hasObject(By.text("Add to GIFs"))) return@repeat
            try {
                device.findObject(By.desc("GIF"))?.longClick()
            } catch (_: StaleObjectException) {
            }
            device.wait(Until.hasObject(By.text("Add to GIFs")), SHORT_WAIT)
        }
        tap(By.text("Add to GIFs"))
        waitFor(By.text("Added to GIFs"), "the GIF kept")
        backTo(By.text("Material Design"), "the chat list")
    }

    /**
     * A contact card and a place, in Lina's chat: the place with its Open in
     * Maps, the card with Add, which answers in a snackbar.
     */
    @Test
    fun contactsAndPlacesShowAsCards() {
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        scrollBackTo(By.text("Blue Bottle Coffee"), "the place")
        waitFor(By.text("Open in Maps"), "the place's button")
        scrollBackTo(By.text("Sasha Kim"), "the contact card")
        screenshot("63-contact-and-place")
        // The card's Add, which is the one under the name: "Add" is also the
        // caption of your own story on the chat list, which stays in the
        // tree beneath the open chat, comes first in it, and took the tap.
        // Pressed again if nothing answers, since the list can still be
        // gliding from the scroll that found the card and a tap on a moving
        // list only stops it. Adding twice is harmless — the number is the key.
        val answer = By.textStartsWith("Sasha Kim ")
        // Counted the moment it shows: the answer is a snackbar, gone in four
        // seconds, and a later look for it missed one that had come and gone.
        var answered = false
        repeat(3) {
            if (answered) return@repeat
            try {
                val below = device.findObject(By.text("Sasha Kim"))?.visibleBounds?.bottom
                if (below != null) {
                    device.findObjects(By.text("Add"))
                        .filter { it.visibleBounds.top > below }
                        .minByOrNull { it.visibleBounds.top }
                        ?.click()
                }
            } catch (_: StaleObjectException) {
            }
            answered = device.wait(Until.hasObject(answer), ANSWER_WAIT)
        }
        assertTrue("the answer to Add never appeared", answered)
        // Above them, a message that is one emoji, drawn large.
        scrollBackTo(By.text("🎉"), "the lone emoji")
        screenshot("66-jumbo-emoji")
        backTo(By.text("Material Design"), "the chat list")
    }

    /**
     * Sending where you are: the paperclip's Location, with the permission
     * already granted from the shell so no system dialog stands in the way,
     * opens "Send your location?". The emulator may or may not have a fix,
     * so the test stops at the dialog and cancels.
     */
    @Test
    fun locationIsOfferedFromThePaperclip() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        device.executeShellCommand("pm grant $app android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $app android.permission.ACCESS_COARSE_LOCATION")
        signIn()
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Lina Park"))
        tap(By.desc("Attach"))
        // The sheet asks for photos first, whichever test meets it first.
        allowPhotos()
        // On screen with the sheet all the way up, as Poll is in the poll
        // test: scrolling for it would drag the sheet itself away.
        tap(By.text("Location"))
        waitFor(By.text("Send your location?"), "the location dialog")
        SystemClock.sleep(1_500)
        screenshot("67-send-location")
        tap(By.text("Cancel"))
        backTo(By.text("Material Design"), "the chat list")
    }

    /** A group's invite link, revoked for a new one from the info screen. */
    @Test
    fun anInviteLinkIsRevokedForANewOne() {
        signIn()
        waitFor(By.text(GROUP_CHAT), "the chat list")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.textContains("Figma dump"), "the group")
        awaitNoHeadsUp()
        tap(By.text(GROUP_CHAT))
        waitFor(By.text("Info"), "the info screen")
        scrollDownTo(By.text("Revoke"))
        tap(By.text("Revoke"))
        waitFor(By.text("Revoke the link?"), "the revoke dialog")
        tap(By.text("Revoke link"))
        waitFor(By.textContains("x1"), "the new link")
        screenshot("64-invite-link")
        // Deleting everything this account said in the group, offered and
        // confirmed — and cancelled here: the demo's messages live as long
        // as the process, and later tests read the group's.
        scrollDownTo(By.text("Delete all my messages"))
        tap(By.text("Delete all my messages"))
        waitFor(By.text("Delete all your messages?"), "the delete-all dialog")
        tap(By.text("Cancel"))
        backTo(By.text(GROUP_CHAT), "the chat list")
    }

    /** Taps the highest of several matches — a screen's header over its content. */
    private fun tapTopmost(selector: BySelector) {
        // A fresh tree first: straight after a screen changes, UiAutomator's
        // accessibility cache can still hold the screen before, and the
        // header it was asked for "is not there" while it is on screen —
        // which is how this test once failed on a chat that had opened fine.
        if (Build.VERSION.SDK_INT >= 34) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
        }
        device.wait(Until.hasObject(selector), STEP_TIMEOUT)
        val node = device.findObjects(selector).minByOrNull { it.visibleBounds.top }
            ?: error("nothing to tap: $selector")
        node.click()
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /** Long-presses the row titled [title] until its menu shows [entry]. */
    private fun longPressChat(title: String, entry: String) {
        repeat(3) {
            if (device.hasObject(By.text(entry))) return
            try {
                device.findObject(By.text(title))?.longClick()
            } catch (_: StaleObjectException) {
            }
            device.wait(Until.hasObject(By.text(entry)), SHORT_WAIT)
        }
        waitFor(By.text(entry), "the menu's $entry")
    }

    /**
     * Back, one press at a time, until [selector] is on screen — never one
     * press past it, which on the chat list would leave the app.
     */
    private fun backTo(selector: BySelector, what: String) {
        repeat(6) {
            if (device.wait(Until.hasObject(selector), SHORT_WAIT)) return
            device.pressBack()
        }
        waitFor(selector, what)
    }

    /** The chat list scrolled back up to its collapsing header, where the menu is. */
    private fun scrollBackToChatMenu() {
        repeat(4) {
            if (device.wait(Until.hasObject(By.desc("More")), SHORT_WAIT)) return
            try {
                device.findObjects(By.scrollable(true))
                    .maxByOrNull { it.visibleBounds.height() }
                    ?.scroll(Direction.UP, 0.8f)
            } catch (_: StaleObjectException) {
            }
        }
    }

    /** Scrolls whatever scrolls on screen down until [selector] shows. */
    private fun scrollDownTo(selector: BySelector) {
        repeat(5) {
            // A fresh tree each look, as tapTopmost takes: Appearance once
            // had "Soft" on screen and UiAutomator's cache still without it.
            if (Build.VERSION.SDK_INT >= 34) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            }
            if (device.wait(Until.hasObject(selector), SHORT_WAIT)) return
            try {
                // The tallest thing that scrolls, which is the page: a row
                // that scrolls sideways — the wallpaper cards — is scrollable
                // too, and scrolling it down does nothing.
                device.findObjects(By.scrollable(true))
                    .maxByOrNull { it.visibleBounds.height() }
                    ?.scroll(Direction.DOWN, 0.6f)
            } catch (_: StaleObjectException) {
            }
        }
        waitFor(selector, selector.toString().filter { it.isLetterOrDigit() })
    }

    /**
     * Types into the field named [description]. The name sits on the
     * field's wrapper, not on the EditText inside it, and setting text on the
     * wrapper does nothing — so the field is tapped, and the text goes into
     * whichever EditText then has focus.
     */
    private fun typeInto(description: String, text: String) {
        tap(By.desc(description))
        val focused = By.clazz("android.widget.EditText").focused(true)
        waitFor(focused, "the $description field focused")
        device.findObject(focused).text = text
        device.waitForIdle(IDLE_TIMEOUT)
    }

    /** Drags the chat list up until [selector] is on screen. */
    private fun scrollChatsTo(selector: BySelector) {
        repeat(8) {
            if (device.hasObject(selector)) return
            dragList(0.8, 0.45)
        }
        waitFor(selector, selector.toString().filter { it.isLetterOrDigit() })
    }

    /** From the chat list to the login screen, through Settings. */
    private fun logOut() {
        waitFor(By.text("Material Design"), "the chat list")
        awaitNoHeadsUp()
        tap(By.text("Settings"))
        waitFor(By.text("Appearance"), "the settings")
        val logOut = By.text("Log out")
        repeat(3) {
            if (device.wait(Until.hasObject(logOut), SHORT_WAIT)) return@repeat
            try {
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            } catch (_: StaleObjectException) {
            }
        }
        tap(logOut)
        // The row asks first. Its dialog's button has the row's own words,
        // so it is picked as the one further right: a dialog's buttons sit
        // at the end, the row's title just after its icon.
        waitFor(By.text("Log out of Telegram?"), "the log-out question")
        device.findObjects(logOut).maxByOrNull { it.visibleBounds.centerX() }!!.click()
        waitFor(By.text("Your phone"), "the login screen")
    }

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

        /** How long one look through a fresh tree waits before the next. */
        const val FRESH_LOOK_MILLIS = 1_000L

        /** A look rather than a wait: whether something is already there. */
        const val SHORT_WAIT = 2_000L

        /** Long enough to see a snackbar that the button's answer is. */
        const val ANSWER_WAIT = 5_000L

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
        /** Long enough past Telegram's one-second shortest video message, with the camera's opening in it. */
        const val VIDEO_NOTE_HOLD_MS = 6_000L
        /** Well past the composer's 72 dp to lock, on a Pixel 6's 2.625 density. */
        const val VIDEO_NOTE_LOCK_SLIDE_PX = 320
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
        const val FORUM_CHAT = "Compose Forum"
        const val MUSIC_CHANNEL = "Material Sound"

        /** The start of the demo group's oldest line; see DEMO_ARCHIVE_FIRST_LINE. */
        const val OLD_HIT_QUERY = "Kickoff"

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
