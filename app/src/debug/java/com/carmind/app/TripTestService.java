package com.carmind.app;

import android.content.Intent;
import java.io.IOException;

/** Non-exported instrumentation fixture. Absent from release; no production Intent can select it. */
public final class TripTestService extends TripRecordingService {
    static final String OWNER = "real-trip-component-test";
    static volatile int failAfter;
    @Override protected String authenticatedOwner() { return OWNER; }
    @Override protected void readConnection(BluetoothObdClient connection, Intent intent,
            BluetoothObdClient.SampleListener callback) throws IOException {
        int samples = 0;
        while (!connection.isClosed()) {
            callback.onSample(new VehicleReading(System.currentTimeMillis(), "TEST TRANSPORT", 1200.0 + samples, 30.0 + samples, 80.0));
            samples++;
            if (failAfter > 0 && samples >= failAfter) throw new IOException("Test disconnection");
            try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }
}
