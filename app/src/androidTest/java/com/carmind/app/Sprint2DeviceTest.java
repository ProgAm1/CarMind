package com.carmind.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Sprint2DeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Test public void historySurvivesRecreationAndIsIsolatedByUser() {
        String uid = "test-" + System.nanoTime();
        ReadingHistory first = new ReadingHistory(context, uid);
        assertTrue(first.save(new VehicleReading(1000, "Test reader", null, 0.0, 90.0)));
        List<VehicleReading> restored = new ReadingHistory(context, uid).load();
        assertEquals(1, restored.size());
        assertNull(restored.get(0).rpm);
        assertEquals(0.0, restored.get(0).speed, 0.0);
        assertEquals(1000, restored.get(0).recordedAt);
        assertTrue(new ReadingHistory(context, uid + "-other").load().isEmpty());
        context.getSharedPreferences("carmind_readings_v1", 0).edit().remove(uid).commit();
    }

    @Test public void historyKeepsNewestTwentyReadings() {
        String uid = "bounded-" + System.nanoTime();
        ReadingHistory history = new ReadingHistory(context, uid);
        for (int i = 1; i <= 25; i++) assertTrue(history.save(new VehicleReading(i, "Test", 800.0, null, null)));
        List<VehicleReading> result = history.load();
        assertEquals(20, result.size());
        assertEquals(25, result.get(0).recordedAt);
        assertEquals(6, result.get(19).recordedAt);
        context.getSharedPreferences("carmind_readings_v1", 0).edit().remove(uid).commit();
    }

    @Test public void corruptHistoryDoesNotCrashOrInventReadings() {
        String uid = "corrupt-" + System.nanoTime();
        context.getSharedPreferences("carmind_readings_v1", 0).edit().putString(uid, "broken json").commit();
        assertTrue(new ReadingHistory(context, uid).load().isEmpty());
        context.getSharedPreferences("carmind_readings_v1", 0).edit().remove(uid).commit();
    }

    @Test public void dashboardRendersValuesAndCapturesEvidence() throws Exception {
        try (ActivityScenario<DashboardPreviewActivity> screen = ActivityScenario.launch(DashboardPreviewActivity.class)) {
            screen.onActivity(activity -> {
                assertEquals("0", ((TextView) activity.findViewById(R.id.speed_value)).getText().toString());
                assertEquals("90", ((TextView) activity.findViewById(R.id.coolant_value)).getText().toString());
                assertEquals(3, ((LinearLayout) activity.findViewById(R.id.history_list)).getChildCount());
            });
            capture("01-reader-screen.png");
            screen.onActivity(activity -> {
                ScrollView root = activity.findViewById(R.id.root);
                root.scrollTo(0, activity.findViewById(R.id.reading_time).getTop() - 80);
            });
            capture("02-reading-screen.png");
            screen.onActivity(activity -> ((ScrollView) activity.findViewById(R.id.root)).fullScroll(ScrollView.FOCUS_DOWN));
            capture("03-history-screen.png");
            screen.recreate();
            screen.onActivity(activity -> assertEquals("90",
                    ((TextView) activity.findViewById(R.id.coolant_value)).getText().toString()));
        }
    }

    @Test public void numberedTripShowsMetricsWithoutLocation() throws Exception {
        Intent intent = new Intent(context, DashboardPreviewActivity.class).putExtra("trip", true);
        try (ActivityScenario<DashboardPreviewActivity> screen = ActivityScenario.launch(intent)) {
            screen.onActivity(a -> {
                String summary = ((TextView) a.findViewById(R.id.real_trip_summary)).getText().toString();
                assertTrue(summary.contains("Trip 3")); assertTrue(summary.contains("RPM"));
                assertTrue(summary.contains("45.0 / 50.0"));
                assertFalse(summary.contains("Jeddah")); assertFalse(summary.contains("destination"));
                android.view.View card = (android.view.View) a.findViewById(R.id.real_trip_summary).getParent();
                ((ScrollView) a.findViewById(R.id.root)).scrollTo(0, card.getTop());
            });
            capture("05-real-trip-layout.png");
        }
    }

    @Test public void emptyStateDoesNotShowSampleValues() throws Exception {
        Intent intent = new Intent(context, DashboardPreviewActivity.class).putExtra("empty", true);
        try (ActivityScenario<DashboardPreviewActivity> screen = ActivityScenario.launch(intent)) {
            screen.onActivity(activity -> {
                assertEquals("Not available", ((TextView) activity.findViewById(R.id.rpm_value)).getText().toString());
                assertFalse(activity.findViewById(R.id.read_vehicle).isEnabled());
            });
            capture("04-empty-screen.png");
        }
    }

    @Test public void signedOutHomeRedirectsToLogin() {
        // This test is for an isolated emulator with no live Firebase configuration/account.
        CarMindApplication application = (CarMindApplication) context.getApplicationContext();
        assertTrue("Use an isolated signed-out emulator",
                application.getAuth() == null || application.getAuth().getCurrentUser() == null);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(LoginActivity.class.getName(), null, false);
        try {
            context.startActivity(new Intent(context, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            android.app.Activity login = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Unauthenticated Home must redirect", login);
            instrumentation.runOnMainSync(login::finish);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync();
        Thread.sleep(500); // Let the compositor present the final frame before taking a screenshot.
        File directory = new File(context.getExternalFilesDir(null), "sprint2-screenshots");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
        try (android.os.ParcelFileDescriptor result = instrumentation.getUiAutomation().executeShellCommand(
                "cp " + new File(directory, name).getAbsolutePath() + " /data/local/tmp/trips-" + name);
                java.io.FileInputStream input = new java.io.FileInputStream(result.getFileDescriptor())) {
            while (input.read() != -1) { /* Preserve evidence before test APK teardown. */ }
        }
    }
}
