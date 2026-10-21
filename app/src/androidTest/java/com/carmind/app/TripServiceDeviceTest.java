package com.carmind.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Build;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TripServiceDeviceTest {
    private final android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Context context = inst.getTargetContext();
    private void waitFor(BooleanSupplier condition) throws Exception {
        long until = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < until) { if (condition.getAsBoolean()) return; Thread.sleep(100); }
        fail("Service did not reach expected state");
    }
    private void run(boolean failConnection) throws Exception {
        context.getSharedPreferences(TripStore.FILE, 0).edit().remove(TripTestService.OWNER).commit();
        TripTestService.failAfter = failConnection ? 3 : 0;
        if (Build.VERSION.SDK_INT >= 31) inst.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.BLUETOOTH_CONNECT);
        AtomicReference<TripRecordingService> service = new AtomicReference<>(); CountDownLatch bound = new CountDownLatch(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { service.set(((TripRecordingService.LocalBinder) binder).service()); bound.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { service.set(null); }
        };
        context.bindService(new Intent(context, TripTestService.class), connection, Context.BIND_AUTO_CREATE);
        assertTrue(bound.await(5, TimeUnit.SECONDS));
        try (ActivityScenario<DashboardPreviewActivity> screen = ActivityScenario.launch(DashboardPreviewActivity.class)) {
            screen.onActivity(a -> {
                Intent start = new Intent(a, TripTestService.class).setAction(TripRecordingService.START).putExtra("owner", TripTestService.OWNER);
                if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(start); else a.startService(start);
            });
            TripStore store = new TripStore(context, TripTestService.OWNER);
            if (failConnection) {
                waitFor(() -> !store.load().isEmpty());
                assertEquals("disconnected", store.load().get(0).reason); assertEquals(3, store.load().get(0).samples);
            } else {
                waitFor(() -> {
                    boolean[] ready = {false}; inst.runOnMainSync(() -> ready[0] = service.get().current() != null && service.get().current().samples >= 2); return ready[0];
                });
                int[] before = {0}; inst.runOnMainSync(() -> before[0] = service.get().current().samples);
                screen.moveToState(Lifecycle.State.CREATED); // Activity backgrounding does not stop recording.
                waitFor(() -> { boolean[] progressed = {false}; inst.runOnMainSync(() -> progressed[0] = service.get().current().samples > before[0] + 2); return progressed[0]; });
                inst.runOnMainSync(() -> service.get().stop("finished", R.string.real_trip_finished));
                assertEquals(1, store.load().size()); TripRecord saved = store.load().get(0);
                assertEquals(1, saved.number); assertEquals("finished", saved.reason); assertTrue(saved.samples >= 5);
                Thread.sleep(500); assertEquals(saved.samples, store.load().get(0).samples);
            }
            assertTrue(new TripStore(context, TripTestService.OWNER + "-other").load().isEmpty());
        } finally {
            if (service.get() != null) inst.runOnMainSync(() -> service.get().stop("finished", R.string.real_trip_finished));
            context.unbindService(connection); context.stopService(new Intent(context, TripTestService.class));
            TripTestService.failAfter = 0;
            context.getSharedPreferences(TripStore.FILE, 0).edit().remove(TripTestService.OWNER).commit();
        }
    }
    @Test public void foregroundRecorderContinuesInBackgroundAndSavesOnStop() throws Exception { run(false); }
    @Test public void disconnectSavesReceivedSamplesAndEndsTrip() throws Exception { run(true); }
}
