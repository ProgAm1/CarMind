package com.carmind.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SimulationDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Before public void prepareAutomation() {
        instrumentation.getUiAutomation().getRootInActiveWindow();
        instrumentation.waitForIdleSync();
    }

    private void chooseReader(ActivityScenario<SimulationPreviewActivity> screen) throws Exception {
        screen.onActivity(a -> a.findViewById(R.id.demo_connect).performClick());
        instrumentation.waitForIdleSync();
        waitFor(() -> {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root == null) return false;
            for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(context.getString(R.string.demo_reader))) {
                AccessibilityNodeInfo target = node;
                while (target != null && !target.isClickable()) target = target.getParent();
                if (target != null) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            return false;
        });
    }
    private void connect(ActivityScenario<SimulationPreviewActivity> screen) throws Exception {
        screen.onActivity(a -> a.findViewById(R.id.demo_connect).performClick());
        assertTrue(value(screen, R.id.demo_status), value(screen, R.id.demo_status).startsWith("Step 2"));
        chooseReader(screen);
        waitFor(() -> value(screen, R.id.demo_status).startsWith("Connected"));
    }
    private String value(ActivityScenario<SimulationPreviewActivity> screen, int id) {
        AtomicReference<String> result = new AtomicReference<>();
        screen.onActivity(a -> result.set(((TextView) a.findViewById(id)).getText().toString()));
        return result.get();
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        long until = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < until) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(150);
        }
        fail("Expected state was not reached within 12 seconds");
    }

    @Test public void walkthroughStreamsPausesDisconnectsAndReconnects() throws Exception {
        try (ActivityScenario<SimulationPreviewActivity> screen = ActivityScenario.launch(SimulationPreviewActivity.class)) {
            assertEquals("—", value(screen, R.id.demo_speed));
            capture("01-connection.png");
            connect(screen);
            capture("02-connected.png");
            String first = value(screen, R.id.demo_speed);
            waitFor(() -> !first.equals(value(screen, R.id.demo_speed)));
            screen.onActivity(a -> a.findViewById(R.id.demo_pause).performClick());
            String paused = value(screen, R.id.demo_speed);
            Thread.sleep(2200);
            assertEquals(paused, value(screen, R.id.demo_speed));
            assertTrue(value(screen, R.id.demo_status).startsWith("Paused"));
            screen.onActivity(a -> a.findViewById(R.id.demo_disconnect).performClick());
            assertTrue(value(screen, R.id.demo_status).startsWith("Connection lost"));
            Thread.sleep(1200);
            assertEquals(paused, value(screen, R.id.demo_speed));
            chooseReader(screen);
            waitFor(() -> value(screen, R.id.demo_status).startsWith("Connected"));
            waitFor(() -> !paused.equals(value(screen, R.id.demo_speed)));
        }
    }

    @Test public void recreationKeepsValuesButRequiresResumeAndBackgroundStopsUpdates() throws Exception {
        try (ActivityScenario<SimulationPreviewActivity> screen = ActivityScenario.launch(SimulationPreviewActivity.class)) {
            connect(screen);
            waitFor(() -> !"0".equals(value(screen, R.id.demo_speed)));
            screen.onActivity(a -> a.findViewById(R.id.demo_pause).performClick());
            String last = value(screen, R.id.demo_speed);
            screen.recreate();
            assertEquals(last, value(screen, R.id.demo_speed));
            assertTrue(value(screen, R.id.demo_status).startsWith("Paused"));
            screen.onActivity(a -> a.findViewById(R.id.demo_pause).performClick());
            screen.moveToState(Lifecycle.State.CREATED);
            Thread.sleep(1200);
            screen.moveToState(Lifecycle.State.RESUMED);
            assertTrue(value(screen, R.id.demo_status).startsWith("Paused"));
        }
    }

    @Test public void simulatedHistoryIsSeparateFromRealAndOtherAccounts() {
        String uid = "simulation-isolation-" + System.nanoTime();
        try {
            ReadingHistory simulated = ReadingHistory.simulation(context, uid);
            assertTrue(simulated.save(new VehicleReading(1000, "SIMULATED", 800.0, 0.0, 76.0)));
            assertTrue(new ReadingHistory(context, uid).load().isEmpty());
            assertTrue(ReadingHistory.simulation(context, uid + "-other").load().isEmpty());
            assertEquals(1, ReadingHistory.simulation(context, uid).load().size());
        } finally {
            context.getSharedPreferences("carmind_demo_readings_v1", 0).edit().remove(uid).commit();
        }
    }

    @Test public void tripPersistsAcrossRecreationPausesAndSavesWithDepartureAndEnd() throws Exception {
        String uid = "simulation-component-test";
        context.getSharedPreferences(DemoTripStore.FILE, 0).edit().remove(uid + ":draft").commit();
        DemoTripStore store = new DemoTripStore(context, uid);
        try (ActivityScenario<SimulationPreviewActivity> screen = ActivityScenario.launch(SimulationPreviewActivity.class)) {
            assertTrue(store.load().size() >= 4);
            connect(screen);
            screen.onActivity(a -> a.findViewById(R.id.trip_start).performClick());
            waitFor(() -> store.draft() != null && store.draft().seconds >= 30);
            screen.onActivity(a -> a.findViewById(R.id.demo_pause).performClick());
            DemoTrip paused = store.draft();
            assertNotNull(paused);
            Thread.sleep(1200);
            assertEquals(paused.seconds, store.draft().seconds);
            screen.recreate();
            assertEquals(paused.id, store.draft().id);
            assertTrue(value(screen, R.id.trip_live).contains(DemoTripText.duration(paused.seconds)));
            screen.onActivity(a -> {
                android.view.View card = (android.view.View) a.findViewById(R.id.trip_number).getParent();
                ((android.widget.ScrollView) a.findViewById(R.id.demo_content).getParent()).scrollTo(0, card.getTop());
            });
            capture("05-trip.png");
            screen.onActivity(a -> a.findViewById(R.id.demo_pause).performClick());
            waitFor(() -> store.draft().seconds > paused.seconds);
            screen.onActivity(a -> a.findViewById(R.id.trip_end).performClick());
            assertNull(store.draft());
            DemoTrip saved = store.load().get(0);
            assertEquals(paused.id, saved.id); assertTrue(saved.time() > saved.startedAt);
            assertEquals(saved.seconds * 1000L, saved.time() - saved.startedAt);
            assertEquals(paused.number, saved.number); assertTrue(saved.ended);
            screen.onActivity(a -> a.findViewById(R.id.trip_history_button).performClick());
            capture("06-trip-history.png");
        }
    }

    @Test public void compactLargeTextAndLandscapeKeepControlsReadable() throws Exception {
        SimulationPreviewActivity.compactLargeTextTest = true;
        try (ActivityScenario<SimulationPreviewActivity> screen = ActivityScenario.launch(SimulationPreviewActivity.class)) {
            capture("03-large-text.png");
            screen.onActivity(a -> {
                assertNoClippedText(a.findViewById(R.id.demo_content));
                a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            waitFor(() -> {
                java.util.concurrent.atomic.AtomicBoolean landscape = new java.util.concurrent.atomic.AtomicBoolean();
                screen.onActivity(a -> landscape.set(a.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        && a.getWindow().getDecorView().getWidth() > a.getWindow().getDecorView().getHeight()));
                return landscape.get();
            });
            screen.onActivity(a -> assertNoClippedText(a.findViewById(R.id.demo_content)));
            capture("04-landscape.png");
        } finally { SimulationPreviewActivity.compactLargeTextTest = false; }
    }
    private void assertNoClippedText(android.view.View view) {
        if (view.getVisibility() == android.view.View.GONE) return;
        if (view instanceof TextView && ((TextView) view).getLayout() != null) {
            android.text.Layout layout = ((TextView) view).getLayout();
            for (int line = 0; line < layout.getLineCount(); line++) assertEquals(0, layout.getEllipsisCount(line));
            assertTrue("Text must fit its view", layout.getHeight() <= view.getHeight()
                    - view.getPaddingTop() - view.getPaddingBottom());
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertNoClippedText(group.getChildAt(i));
        }
    }

    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync();
        Thread.sleep(600); // Wait for rotation/window animation to reach the compositor.
        java.io.File directory = new java.io.File(context.getExternalFilesDir(null), "classroom-screenshots");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        android.graphics.Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name))) {
            assertNotNull(bitmap);
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { if (bitmap != null) bitmap.recycle(); }
        try (android.os.ParcelFileDescriptor result = instrumentation.getUiAutomation().executeShellCommand(
                "cp " + new java.io.File(directory, name).getAbsolutePath() + " /data/local/tmp/classroom-" + name);
                java.io.FileInputStream input = new java.io.FileInputStream(result.getFileDescriptor())) {
            while (input.read() != -1) { /* Wait for the copy before teardown. */ }
        }
    }

    @Test public void realSimulationScreenRequiresFirebaseSignIn() {
        CarMindApplication app = (CarMindApplication) context.getApplicationContext();
        assertTrue("Use a signed-out test emulator", app.getAuth() == null || app.getAuth().getCurrentUser() == null);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(LoginActivity.class.getName(), null, false);
        try {
            context.startActivity(new Intent(context, SimulationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            android.app.Activity login = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Simulation must not bypass authentication", login);
            instrumentation.runOnMainSync(login::finish);
        } finally { instrumentation.removeMonitor(monitor); }
    }
}
