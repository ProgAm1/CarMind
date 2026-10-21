package com.carmind.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DemoTripStoreTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void clean(String uid) {
        context.getSharedPreferences(DemoTripStore.FILE, 0).edit().remove(uid + ":history")
                .remove(uid + ":draft").remove(uid + ":initialized").commit();
    }
    @Test public void twoAccountsHaveDifferentPersistentHistoryAndPrivateDrafts() {
        String a = "trips-a-" + System.nanoTime(), b = "trips-b-" + System.nanoTime();
        try {
            DemoTripStore first = new DemoTripStore(context, a), second = new DemoTripStore(context, b);
            first.initializeExamples(System.currentTimeMillis()); second.initializeExamples(System.currentTimeMillis());
            assertEquals(4, first.load().size()); assertEquals(4, second.load().size());
            assertNotEquals(first.load().get(0).id, second.load().get(0).id);
            DemoTrip active = new DemoTrip(1, System.currentTimeMillis()); active.tick(40); first.saveDraft(active);
            assertNull(second.draft());
            DemoTripStore reopened = new DemoTripStore(context, a);
            assertEquals(active.id, reopened.draft().id); assertEquals(30, reopened.draft().seconds);
            String original = reopened.load().get(0).id;
            reopened.initializeExamples(System.currentTimeMillis() + 86400000);
            assertEquals(original, reopened.load().get(0).id);
        } finally { clean(a); clean(b); }
    }
    @Test public void finishingIsIdempotentAndDoesNotReplaceAnotherDraft() {
        String uid = "finish-" + System.nanoTime();
        try {
            DemoTripStore store = new DemoTripStore(context, uid);
            DemoTrip trip = new DemoTrip(1, 1000); trip.tick(30); store.saveDraft(trip);
            trip.finish(); store.finish(trip); store.finish(trip);
            assertNull(store.draft()); assertEquals(1, store.load().size());
            DemoTrip next = new DemoTrip(1, 100000); store.saveDraft(next);
            store.finish(trip); assertEquals(next.id, store.draft().id);
        } finally { clean(uid); }
    }
    @Test public void historyIsBoundedAndSurvivesStoreRecreation() {
        String uid = "bounded-trips-" + System.nanoTime();
        try {
            DemoTripStore store = new DemoTripStore(context, uid);
            for (int i = 1; i <= 25; i++) {
                DemoTrip trip = new DemoTrip(i, i * 100000L);
                trip.tick(30); trip.finish(); store.finish(trip);
            }
            store = new DemoTripStore(context, uid);
            assertEquals(20, store.load().size()); assertEquals(2500000, store.load().get(0).startedAt);
            assertEquals(600000, store.load().get(19).startedAt);
        } finally { clean(uid); }
    }
    @Test public void corruptRecordsDoNotInventReplacementTrips() {
        String uid = "corrupt-trips-" + System.nanoTime();
        try {
            context.getSharedPreferences(DemoTripStore.FILE, 0).edit().putBoolean(uid + ":initialized", true)
                    .putString(uid + ":history", "bad json").putString(uid + ":draft", "bad json").commit();
            DemoTripStore store = new DemoTripStore(context, uid); store.initializeExamples(System.currentTimeMillis());
            assertTrue(store.load().isEmpty()); assertNull(store.draft());
        } finally { clean(uid); }
    }
}
