package com.carmind.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One RFCOMM connection; bounded setup and reads. Only supported, read-only PIDs. */
final class BluetoothObdClient implements AutoCloseable {
    private static final UUID SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private BluetoothSocket socket;
    private volatile boolean closed;
    interface SampleListener { void onSample(VehicleReading sample); }

    VehicleReading read(BluetoothDevice device, String name) throws IOException {
        final VehicleReading[] result = new VehicleReading[1];
        run(device, name, sample -> result[0] = sample, false);
        return result[0];
    }
    void stream(BluetoothDevice device, String name, SampleListener listener) throws IOException {
        run(device, name, listener, true);
    }
    @SuppressLint("MissingPermission") // Caller checks CONNECT; revocation is surfaced to caller.
    private void run(BluetoothDevice device, String name, SampleListener listener, boolean continuous) throws IOException {
        ScheduledExecutorService timeout = Executors.newSingleThreadScheduledExecutor();
        try {
            synchronized (this) {
                if (closed) throw new IOException("Cancelled");
                socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT);
            }
            ScheduledFuture<?> deadline = timeout.schedule(this::close, 40, TimeUnit.SECONDS);
            byte[] supported;
            try {
                socket.connect();
                supported = ObdProtocol.initialize(command -> exchange(command));
            } finally { deadline.cancel(false); }
            while (!closed) {
                deadline = timeout.schedule(this::close, 15, TimeUnit.SECONDS);
                Double[] values;
                try { values = ObdProtocol.sample(command -> exchange(command), supported); }
                finally { deadline.cancel(false); }
                if (closed) throw new IOException("Cancelled or timed out");
                listener.onSample(new VehicleReading(System.currentTimeMillis(), name, values[0], values[1], values[2]));
                if (!continuous) return;
                try { Thread.sleep(1000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
            }
            throw new IOException("Disconnected");
        } finally { timeout.shutdownNow(); close(); }
    }
    private String exchange(String command) throws IOException {
        if (closed) throw new IOException("Cancelled");
        return ObdProtocol.exchange(socket.getInputStream(), socket.getOutputStream(), command);
    }
    boolean isClosed() { return closed; }
    @Override public synchronized void close() {
        closed = true;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { /* Already disconnected. */ }
        }
    }
}
