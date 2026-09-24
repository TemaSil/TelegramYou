package com.telegramyou.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.telegramyou.app.telegram.TelegramForegroundService
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.MessageUpdate
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The live client signed in — on Telegram's test servers, where that needs
 * nobody's phone — sending a photo to its own Saved Messages.
 *
 * Numbers of the form +99966XYYYY exist there for testing, X being the data
 * centre, and each one's login code is X five times. A number nobody has used
 * yet is registered on the spot, which the client does by itself.
 *
 * This is the test for "I can't send a photo": the whole path the composer
 * takes — a content Uri from this app's own provider, copied into TDLib's
 * upload directory, sent, uploaded — ending in either the server's
 * confirmation or, since this client started listening for it, the server's
 * refusal, whose words then fail the test.
 *
 * Runs only in a build made with `-PtelegramTestDc=true`, which the Live
 * workflow makes for it and never publishes.
 */
@RunWith(AndroidJUnit4::class)
class TestDcClientTest {

    private lateinit var device: UiDevice

    @Before
    fun onlyAgainstTheTestServers() {
        assumeTrue("needs a live build on the test servers", BuildConfig.USE_TEST_DC)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun signsInAndSendsAPhotoToSavedMessages() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.context.startActivity(launchIntent())
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), LAUNCH_TIMEOUT)

        val repository = (context.applicationContext as TelegramYouApp).telegramRepository
        val auth = repository.observeAuth()
        awaitAuth(auth.value) { auth.value }.let { state ->
            assertEquals("TDLib did not start: ${state.errorMessage}", AuthState.WaitPhoneNumber, state.state)
        }

        val phone = "+99966$DC${Random.nextInt(1000, 10000)}"
        repository.submitPhoneNumber(phone)
        val afterPhone = awaitStep(setOf(AuthState.WaitCode, AuthState.Ready)) { auth.value }
        if (afterPhone.state != AuthState.WaitCode) {
            fail("$phone was not accepted: ${afterPhone.state} ${afterPhone.errorMessage}")
        }
        repository.submitCode(DC.toString().repeat(5))
        val signedIn = awaitStep(setOf(AuthState.Ready, AuthState.WaitPassword)) { auth.value }
        instrumentation.uiAutomation.takeScreenshot().writeToTestStorage("testdc-01-signed-in")
        assertEquals(
            "$phone did not get in: ${signedIn.errorMessage}",
            AuthState.Ready,
            signedIn.state
        )

        val me = signedIn.me ?: auth.value.me ?: error("signed in with no account")
        val savedMessages = repository.openPrivateChat(me.id)

        // A picture the size of a phone photo's thumbnail, through the same
        // provider the camera button writes to.
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "test-dc.jpg")
        FileOutputStream(file).use { out -> testPicture().compress(Bitmap.CompressFormat.JPEG, 90, out) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        // Listening before sending: the answer can arrive before send returns.
        val outcome = async(start = CoroutineStart.UNDISPATCHED) {
            repository.messageUpdates
                .filter { it.chatId == savedMessages }
                .first { it is MessageUpdate.Replaced || it is MessageUpdate.SendFailed }
        }
        repository.sendMessage(savedMessages, "From the test servers", AttachmentDraft.Photos(listOf(uri.toString())))
        val answer = withTimeout(SEND_TIMEOUT) { outcome.await() }

        // The conversation, opened the way a notification opens it.
        instrumentation.context.startActivity(
            launchIntent().putExtra(TelegramForegroundService.EXTRA_CHAT_ID, savedMessages)
        )
        Thread.sleep(SETTLE)
        instrumentation.uiAutomation.takeScreenshot().writeToTestStorage("testdc-02-photo-sent")

        if (answer is MessageUpdate.SendFailed) {
            fail("The photo was refused: ${answer.error}")
        }
        assertTrue(answer is MessageUpdate.Replaced)
    }

    private fun launchIntent(): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return (context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("no launcher activity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** Until TDLib is past starting: at the phone step, or failed. */
    private fun awaitAuth(first: AuthUiState, read: () -> AuthUiState): AuthUiState {
        val deadline = System.currentTimeMillis() + TDLIB_TIMEOUT
        var state = first
        while (System.currentTimeMillis() < deadline) {
            state = read()
            if (state.state == AuthState.WaitPhoneNumber && !state.isLoading) return state
            if (state.state == AuthState.Error) return state
            Thread.sleep(POLL)
        }
        return state
    }

    /** Until the client reaches one of [targets], says why not, or fails. */
    private fun awaitStep(targets: Set<AuthState>, read: () -> AuthUiState): AuthUiState {
        val deadline = System.currentTimeMillis() + TDLIB_TIMEOUT
        var state = read()
        while (System.currentTimeMillis() < deadline) {
            state = read()
            if (state.state in targets || state.state == AuthState.Error) return state
            if (!state.isLoading && state.errorMessage != null) return state
            Thread.sleep(POLL)
        }
        return state
    }

    private fun testPicture(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x0B, 0x6E, 0x60))
        canvas.drawCircle(320f, 240f, 150f, Paint().apply { color = Color.WHITE })
        return bitmap
    }

    private companion object {
        /** Test data centre 2: the one TDLib's own examples use. */
        const val DC = 2
        const val LAUNCH_TIMEOUT = 30_000L
        const val TDLIB_TIMEOUT = 90_000L
        const val SEND_TIMEOUT = 120_000L
        const val SETTLE = 4_000L
        const val POLL = 500L
    }
}
