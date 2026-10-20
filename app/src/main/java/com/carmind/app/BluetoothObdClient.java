package com.carmind.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One bounded snapshot per connection, always on a worker thread. */
final class BluetoothObdClient implements AutoCloseable {
    private static final UUID SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private BluetoothSocket socket;
    private boolean closed;

    @SuppressLint("MissingPermission") // HomeActivity checks CONNECT immediately before calling.
    VehicleReading read(BluetoothDevice device, String name) throws IOException {
        ScheduledExecutorService timeout = Executors.newSingleThreadScheduledExecutor();
        try {
            synchronized (this) {
                if (closed) throw new IOException("Cancelled");
                socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT);
            }
            // Closing a BluetoothSocket interrupts both connect() and blocking stream reads.
            timeout.schedule(this::close, 40, TimeUnit.SECONDS);
            socket.connect();
            Double[] values = ObdProtocol.read(command ->
                    ObdProtocol.exchange(socket.getInputStream(), socket.getOutputStream(), command));
            synchronized (this) {
                if (closed) throw new IOException("Cancelled or timed out");
            }
            return new VehicleReading(System.currentTimeMillis(), name, values[0], values[1], values[2]);
        } finally {
            timeout.shutdownNow();
            close();
        }
    }

    @Override public synchronized void close() {
        closed = true;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { /* Already disconnected. */ }
        }
    }
}
