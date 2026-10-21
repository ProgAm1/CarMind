package com.carmind.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TripStoreDeviceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void numbersPersistPerAccountAndRealTripsHaveNoGeneratedExamples() {
        String a = "real-a-" + System.nanoTime(), b = a + "-b";
        try {
            TripStore one = new TripStore(context, a), two = new TripStore(context, b);
            assertTrue(one.load().isEmpty()); assertTrue(two.load().isEmpty());
            TripRecord trip = one.begin(1000); assertEquals(1, trip.number);
            trip.add(new VehicleReading(1000, "OBD", null, 30.0, null), 0); one.save(trip);
            trip.finish("finished", 5000); one.finish(trip); one.finish(trip);
            assertEquals(1, one.load().size()); assertTrue(two.load().isEmpty());
            assertEquals(2, new TripStore(context, a).begin(10000).number);
            assertEquals(1, two.begin(10000).number);
            assertTrue(ReadingHistory.simulation(context, a).load().isEmpty());
        } finally { context.getSharedPreferences(TripStore.FILE, 0).edit().remove(a).remove(b).commit(); }
    }
    @Test public void interruptedDraftRecoversAtLastSampleAndNotAtReopenTime() {
        String uid = "real-recovery-" + System.nanoTime();
        try {
            TripStore store = new TripStore(context, uid); TripRecord trip = store.begin(1000);
            trip.add(new VehicleReading(1000, "OBD", 900.0, null, 80.0), 0);
            trip.add(new VehicleReading(4000, "OBD", 1000.0, null, 82.0), 3000); store.save(trip);
            store = new TripStore(context, uid); store.recover(); store.recover();
            assertEquals(1, store.load().size()); TripRecord saved = store.load().get(0);
            assertEquals("interrupted", saved.reason); assertEquals(4000, saved.endedAt());
            assertEquals(0, saved.speedSamples); assertEquals(2, saved.rpmSamples);
            assertEquals(2, store.begin(100000).number);
        } finally { context.getSharedPreferences(TripStore.FILE, 0).edit().remove(uid).commit(); }
    }
    @Test public void boundedHistoryRetainsStableNumbersAndEmptyDraftCreatesNoTrip() {
        String uid = "real-retention-" + System.nanoTime();
        try {
            TripStore store = new TripStore(context, uid);
            TripRecord empty = store.begin(1000); empty.finish("interrupted", 0); store.finish(empty);
            assertTrue(store.load().isEmpty());
            for (int i = 0; i < 102; i++) {
                TripRecord trip = store.begin(10000 + i * 1000);
                trip.add(new VehicleReading(trip.startedAt, "OBD", null, 0.0, null), 0);
                trip.finish("finished", 500); store.finish(trip);
            }
            assertEquals(100, store.load().size()); assertEquals(103, store.load().get(0).number);
            assertEquals(4, store.load().get(99).number);
        } finally { context.getSharedPreferences(TripStore.FILE, 0).edit().remove(uid).commit(); }
    }
    @Test public void legacyDemoTripsLoseRoutesButKeepDatesMetricsAndStableNumbers() throws Exception {
        String uid = "legacy-demo-" + System.nanoTime();
        try {
            org.json.JSONObject old = new org.json.JSONObject().put("id", "old").put("route", 4).put("start", 1000)
                    .put("seconds", 60).put("km", 0.5).put("max", 40).put("ended", true);
            context.getSharedPreferences(DemoTripStore.FILE, 0).edit().putBoolean(uid + ":initialized", true)
                    .putString(uid + ":history", new org.json.JSONArray().put(old).toString()).commit();
            DemoTripStore store = new DemoTripStore(context, uid); DemoTrip trip = store.load().get(0);
            assertEquals(1, trip.number); assertEquals(1000, trip.startedAt); assertEquals(0.5, trip.kilometers, 0);
            assertEquals(2, store.nextNumber());
            DemoTrip next = new DemoTrip(store.nextNumber(), 100000); next.tick(30); next.finish(); store.finish(next);
            assertEquals(1, store.load().get(1).number); // Adding a new trip cannot renumber an older record.
            assertFalse(DemoTripText.summary(context, trip).contains("Jeddah"));
            assertFalse(DemoTripText.summary(context, trip).contains("destination"));
        } finally { context.getSharedPreferences(DemoTripStore.FILE, 0).edit().remove(uid + ":history").remove(uid + ":initialized").commit(); }
    }
}
