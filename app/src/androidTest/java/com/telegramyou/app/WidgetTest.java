package com.telegramyou.app;

import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.services.storage.TestStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Puts each home-screen widget on a host and waits for it to draw.
 *
 * 2.1's widgets reached phones showing Glance's loading spinner for good, and
 * nothing had ever placed one: a widget draws only for a host that has bound
 * it, and no test was a host. This one is — through AppWidgetHost, the API
 * every launcher uses — after granting itself the right to bind, as a
 * launcher holds it.
 *
 * Java on purpose. The Release check runs it against the R8-shrunk release,
 * and a Kotlin test leans on the Kotlin standard library inside the app, which
 * R8 has renamed and pruned; this leans on nothing but Android, JUnit and the
 * test runner. Components are named by string for the same reason.
 *
 * It holds for either backend: the demo's widget lists chats, a live one
 * that has not signed in says to open the app — both under the same "Chats"
 * header, which the loading layout does not have.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetTest {

    private static final int HOST_ID = 0x7e1d;
    private static final long DRAW_TIMEOUT_MS = 45_000;

    private Instrumentation instrumentation;
    private Context context;
    private AppWidgetHost host;
    private final List<Integer> bound = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        shell("appwidget grantbind --package " + context.getPackageName() + " --user 0");
        instrumentation.runOnMainSync(() -> {
            host = new AppWidgetHost(context, HOST_ID);
            host.startListening();
        });
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> {
            for (int id : bound) host.deleteAppWidgetId(id);
            host.stopListening();
        });
    }

    @Test
    public void recentChatsDrawsItsList() throws IOException {
        AppWidgetHostView view = place("com.telegramyou.app.widgets.RecentChatsWidgetReceiver", 900, 700);
        waitForText(view, "Chats", "99-widget-recent-chats");
    }

    @Test
    public void nowPlayingDrawsItsPlayer() throws IOException {
        AppWidgetHostView view = place("com.telegramyou.app.widgets.NowPlayingWidgetReceiver", 1000, 260);
        waitForText(view, "Nothing playing", "99-widget-now-playing");
    }

    @Test
    public void contactPhotoAsksWhoBeforeAnyoneIsChosen() throws IOException {
        // Placed without its setup having run, as a host that skips it would.
        AppWidgetHostView view = place("com.telegramyou.app.widgets.PersonWidgetReceiver", 480, 480);
        waitForText(view, "Choose someone", "99-widget-contact-photo");
    }

    /** Binds a widget of [receiver]'s kind, as a launcher does when one is dropped on it. */
    private AppWidgetHostView place(String receiver, int width, int height) {
        ComponentName provider = new ComponentName(context.getPackageName(), receiver);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetHostView[] view = new AppWidgetHostView[1];
        instrumentation.runOnMainSync(() -> {
            int id = host.allocateAppWidgetId();
            bound.add(id);
            assertTrue("Could not bind " + receiver, manager.bindAppWidgetIdIfAllowed(id, provider));
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            view[0] = host.createView(context, id, info);
            view[0].measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            );
            view[0].layout(0, 0, width, height);
        });
        return view[0];
    }

    /** Waits for [text] to be drawn, then keeps a picture of the widget either way. */
    private void waitForText(AppWidgetHostView view, String text, String shot) throws IOException {
        long deadline = SystemClock.uptimeMillis() + DRAW_TIMEOUT_MS;
        List<String> seen = new ArrayList<>();
        while (SystemClock.uptimeMillis() < deadline) {
            seen.clear();
            instrumentation.runOnMainSync(() -> {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(view.getHeight(), View.MeasureSpec.EXACTLY)
                );
                view.layout(0, 0, view.getWidth(), view.getHeight());
                texts(view, seen);
            });
            if (seen.contains(text)) {
                picture(view, shot);
                return;
            }
            SystemClock.sleep(500);
        }
        picture(view, shot);
        fail("The widget never drew \"" + text + "\" in " + DRAW_TIMEOUT_MS / 1000 + " s; it shows " + seen);
    }

    private static void texts(View view, List<String> into) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) into.add(text.toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) texts(group.getChildAt(i), into);
        }
    }

    private void picture(AppWidgetHostView view, String name) throws IOException {
        Bitmap[] bitmap = new Bitmap[1];
        instrumentation.runOnMainSync(() -> {
            bitmap[0] = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap[0]));
        });
        try (OutputStream out = new TestStorage().openOutputFile(name + ".png")) {
            bitmap[0].compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** Runs a shell command and waits for it to finish. */
    private void shell(String command) throws IOException {
        ParcelFileDescriptor output = instrumentation.getUiAutomation().executeShellCommand(command);
        try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) { /* drain until the command ends */ }
        }
    }
}
