package com.carmind.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the live connection across screen rotation, backgrounding and screen lock. */
public class TripRecordingService extends Service {
    static final String START = "com.carmind.app.START_TRIP", STOP = "com.carmind.app.STOP_TRIP";
    private static final String CHANNEL = "vehicle_trips";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LocalBinder binder = new LocalBinder();
    private FirebaseAuth auth;
    private BluetoothObdClient client;
    private PowerManager.WakeLock wakeLock;
    private TripStore store;
    private TripRecord trip;
    private String owner;
    private long firstElapsed;
    private int generation;
    private boolean running;
    private VehicleReading latest;
    private int message = R.string.real_trip_ready;
    private final Runnable limit = () -> stop("interrupted", R.string.real_trip_limit);
    private final FirebaseAuth.AuthStateListener authListener = value -> {
        if (running && !validOwner()) stop("signed_out", R.string.real_trip_signed_out);
    };
    final class LocalBinder extends Binder { TripRecordingService service() { return TripRecordingService.this; } }
    boolean isRunning() { return running; }
    String owner() { return owner; }
    TripRecord current() { return trip; }
    VehicleReading latest() { return latest; }
    int message() { return message; }

    @Override public void onCreate() {
        super.onCreate();
        auth = ((CarMindApplication) getApplication()).getAuth();
        if (auth != null) auth.addAuthStateListener(authListener);
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, getString(R.string.real_trip_channel), NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) stop("finished", R.string.real_trip_finished);
        else if (intent != null && START.equals(intent.getAction())) start(intent);
        else stopSelf();
        return START_NOT_STICKY; // Never silently reconnect or invent a continuing trip after process death.
    }
    protected String authenticatedOwner() {
        return auth == null || auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }
    private boolean validOwner() { return owner != null && owner.equals(authenticatedOwner()); }
    @SuppressLint("MissingPermission")
    protected void readConnection(BluetoothObdClient connection, Intent intent, BluetoothObdClient.SampleListener listener) throws IOException {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        if (manager == null || manager.getAdapter() == null || !manager.getAdapter().isEnabled()) throw new IOException("Bluetooth unavailable");
        BluetoothDevice device = manager.getAdapter().getRemoteDevice(intent.getStringExtra("address"));
        connection.stream(device, intent.getStringExtra("name"), listener);
    }
    @SuppressLint({"MissingPermission", "WakelockTimeout"})
    private void start(Intent intent) {
        if (running) return;
        owner = intent.getStringExtra("owner");
        latest = null; trip = null;
        if (!validOwner() || (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)) {
            message = R.string.bluetooth_permission; stopSelf(); return;
        }
        try {
            Notification note = notification();
            if (Build.VERSION.SDK_INT >= 29) startForeground(41, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(41, note);
            store = new TripStore(this, owner); store.recover();
            running = true; message = R.string.real_trip_connecting;
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CarMind:TripRecording");
            wakeLock.acquire(86400000L);
            main.postDelayed(limit, 86400000L);
            int request = ++generation;
            BluetoothObdClient connection = new BluetoothObdClient(); client = connection;
            worker.execute(() -> {
                try {
                    readConnection(connection, intent, sample -> {
                        long elapsed = SystemClock.elapsedRealtime();
                        main.post(() -> accept(request, sample, elapsed));
                    });
                } catch (IOException | SecurityException | IllegalArgumentException failure) {
                    main.post(() -> { if (running && request == generation) stop("disconnected", R.string.real_trip_disconnected); });
                }
            });
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            stop("interrupted", failure instanceof IllegalStateException ? R.string.storage_failed : R.string.real_trip_start_failed);
        }
    }
    private void accept(int request, VehicleReading sample, long elapsed) {
        if (!running || request != generation) return;
        if (!validOwner()) { stop("signed_out", R.string.real_trip_signed_out); return; }
        try {
            if (trip == null) { trip = store.begin(sample.recordedAt); firstElapsed = elapsed; }
            trip.add(sample, elapsed - firstElapsed);
            store.save(trip);
            latest = sample; message = R.string.real_trip_recording;
        } catch (IllegalStateException failure) { stop("interrupted", R.string.storage_failed); }
    }
    void stop(String reason, int result) {
        generation++; running = false; message = result;
        main.removeCallbacks(limit);
        if (client != null) { client.close(); client = null; }
        if (trip != null && trip.reason.equals("recording")) {
            // Lost connections/process recovery end at the last successful sample, never fabricated data.
            long end = reason.equals("finished") || reason.equals("signed_out")
                    ? SystemClock.elapsedRealtime() - firstElapsed : trip.durationMs;
            trip.finish(reason, end);
            try { store.finish(trip); }
            catch (IllegalStateException failure) { message = R.string.storage_failed; }
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private Notification notification() {
        Intent open = new Intent(this, LoginActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 41, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent end = PendingIntent.getService(this, 42, new Intent(this, getClass()).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_carmind).setContentTitle(getString(R.string.real_trip_notification))
                .setContentText(getString(R.string.real_trip_notification_detail)).setContentIntent(content).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.real_trip_stop), end).build()).build();
    }
    @Override public void onDestroy() {
        stop("interrupted", R.string.real_trip_interrupted);
        if (auth != null) auth.removeAuthStateListener(authListener);
        worker.shutdownNow(); super.onDestroy();
    }
}
