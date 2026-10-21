package com.carmind.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Authenticated, read-only OBD dashboard. */
public class HomeActivity extends Activity {
    private static final int CONNECT_PERMISSION = 20;
    private FirebaseAuth auth;
    private BluetoothAdapter adapter;
    private BluetoothDevice selected;
    private String selectedName, owner;
    private ReadingHistory history;
    private BluetoothObdClient client;
    private AlertDialog picker;
    private boolean navigating, active, busy;
    private int generation;
    private TripRecordingService recorder;
    private TripStore tripStore;
    private boolean bound, tripStarting;
    private long lastDisplayedAt;
    private VehicleReading displayedTripSample;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (active) { renderTrip(); ui.postDelayed(this, 1000); } }
    };
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            recorder = ((TripRecordingService.LocalBinder) binder).service();
            if (!recorder.isRunning()) {
                try { tripStore.recover(); } catch (IllegalStateException failure) { status(R.string.storage_failed); }
            }
            renderTrip();
        }
        @Override public void onServiceDisconnected(ComponentName name) { recorder = null; renderTrip(); }
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final FirebaseAuth.AuthStateListener listener = value -> {
        if (value.getCurrentUser() == null
                || (owner != null && !owner.equals(value.getCurrentUser().getUid()))) openLogin();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ScreenInsets.apply(findViewById(R.id.root));
        findViewById(R.id.root).setVisibility(View.INVISIBLE);
        auth = ((CarMindApplication) getApplication()).getAuth();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        findViewById(R.id.real_trip_start).setOnClickListener(view -> startTrip());
        findViewById(R.id.real_trip_stop).setOnClickListener(view -> {
            if (recorder != null && owner.equals(recorder.owner())) recorder.stop("finished", R.string.real_trip_finished);
            renderTrip();
        });
        findViewById(R.id.real_trip_history).setOnClickListener(view -> showTrips());
        findViewById(R.id.choose_reader).setOnClickListener(view -> chooseReader());
        findViewById(R.id.classroom_demo).setOnClickListener(view ->
                startActivity(new Intent(this, SimulationActivity.class)));
        findViewById(R.id.bluetooth_settings).setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        findViewById(R.id.read_vehicle).setOnClickListener(view -> readVehicle());
        findViewById(R.id.cancel_reading).setOnClickListener(view -> {
            cancelReading();
            status(R.string.read_cancelled);
        });
        findViewById(R.id.sign_out).setOnClickListener(view -> {
            cancelReading();
            if (recorder != null && owner.equals(recorder.owner())) recorder.stop("signed_out", R.string.real_trip_signed_out);
            if (auth != null) auth.signOut();
            openLogin();
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (auth == null || auth.getCurrentUser() == null) {
            openLogin();
            return;
        }
        active = true;
        owner = auth.getCurrentUser().getUid();
        history = new ReadingHistory(this, owner);
        tripStore = new TripStore(this, owner);
        bound = bindService(new Intent(this, TripRecordingService.class), serviceConnection, BIND_AUTO_CREATE);
        ui.post(refresh);
        ((TextView) findViewById(R.id.account_email)).setText(auth.getCurrentUser().getEmail());
        List<VehicleReading> savedReadings = history.load();
        lastDisplayedAt = savedReadings.isEmpty() ? 0 : savedReadings.get(0).recordedAt;
        DashboardRenderer.render(findViewById(R.id.root), savedReadings);
        findViewById(R.id.root).setVisibility(View.VISIBLE);
        auth.addAuthStateListener(listener);
        setBusy(false);
        if (adapter == null) status(R.string.bluetooth_missing);
    }

    private boolean permitted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private boolean bluetoothReady() {
        if (adapter == null) { status(R.string.bluetooth_missing); return false; }
        if (!permitted()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, CONNECT_PERMISSION);
            return false;
        }
        try {
            if (!adapter.isEnabled()) { status(R.string.bluetooth_off); return false; }
            return true;
        } catch (SecurityException revoked) {
            status(R.string.bluetooth_permission);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private void chooseReader() {
        if (busy || recording() || !bluetoothReady()) return;
        try {
            List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
            devices.sort(Comparator.comparing(this::deviceLabel));
            if (devices.isEmpty()) { status(R.string.no_paired_readers); return; }
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) labels[i] = deviceLabel(devices.get(i));
            picker = new AlertDialog.Builder(this).setTitle(R.string.choose_reader_title)
                    .setItems(labels, (dialog, index) -> {
                        selected = devices.get(index);
                        selectedName = labels[index];
                        ((TextView) findViewById(R.id.reader_name)).setText(selectedName);
                        setBusy(false);
                        status(R.string.device_ready);
                    }).setNegativeButton(R.string.cancel, null).show();
        } catch (SecurityException revoked) { status(R.string.bluetooth_permission); }
    }

    @SuppressLint("MissingPermission")
    private String deviceLabel(BluetoothDevice device) {
        String name = device.getName();
        if (name == null || name.isEmpty()) name = getString(R.string.reader_unnamed);
        String address = device.getAddress();
        return name + " (" + address.substring(Math.max(0, address.length() - 5)) + ")";
    }

    private void readVehicle() {
        if (busy || recording() || selected == null || !bluetoothReady() || !active) return;
        if (auth.getCurrentUser() == null || !owner.equals(auth.getCurrentUser().getUid())) {
            openLogin();
            return;
        }
        setBusy(true);
        status(R.string.reading_vehicle);
        final int request = ++generation;
        final BluetoothObdClient connection = new BluetoothObdClient();
        final BluetoothDevice device = selected;
        final String name = selectedName;
        final ReadingHistory userHistory = history;
        client = connection;
        worker.execute(() -> {
            try {
                VehicleReading reading = connection.read(device, name);
                // Saving uses the original UID even if the user leaves before completion.
                boolean saved = userHistory.save(reading);
                runOnUiThread(() -> {
                    if (!active || request != generation) return;
                    client = null;
                    setBusy(false);
                    status(saved ? R.string.read_saved : R.string.storage_failed);
                    lastDisplayedAt = reading.recordedAt;
                    DashboardRenderer.render(findViewById(R.id.root), userHistory.load());
                });
            } catch (IOException | SecurityException failure) {
                runOnUiThread(() -> {
                    if (!active || request != generation) return;
                    client = null;
                    setBusy(false);
                    status(failure instanceof SecurityException
                            ? R.string.bluetooth_permission : R.string.read_failed);
                });
            }
        });
    }

    private void cancelReading() {
        generation++;
        if (client != null) { client.close(); client = null; }
        setBusy(false);
    }

    private void setBusy(boolean value) {
        busy = value;
        value = value || recording();
        findViewById(R.id.choose_reader).setEnabled(!value && adapter != null);
        findViewById(R.id.bluetooth_settings).setEnabled(!value && adapter != null);
        findViewById(R.id.read_vehicle).setEnabled(!value && selected != null);
        ((Button) findViewById(R.id.read_vehicle)).setText(busy ? R.string.reading_vehicle : R.string.read_vehicle);
        findViewById(R.id.reading_progress).setVisibility(busy ? View.VISIBLE : View.GONE);
        findViewById(R.id.cancel_reading).setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private boolean recording() { return tripStarting || (recorder != null && recorder.isRunning()); }

    @SuppressLint("MissingPermission")
    private void startTrip() {
        if (!active || busy || recording() || selected == null || !bluetoothReady()) return;
        if (auth == null || auth.getCurrentUser() == null || !owner.equals(auth.getCurrentUser().getUid())) { openLogin(); return; }
        if (selected.getType() == BluetoothDevice.DEVICE_TYPE_LE) { status(R.string.real_trip_ble); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 21);
            // A denied notification permission does not prevent connected-device foreground recording.
        }
        Intent start = new Intent(this, TripRecordingService.class).setAction(TripRecordingService.START)
                .putExtra("owner", owner).putExtra("address", selected.getAddress()).putExtra("name", selectedName);
        try {
            tripStarting = true;
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start);
            ui.postDelayed(() -> { tripStarting = false; if (active) renderTrip(); }, 1000);
            renderTrip();
        } catch (IllegalStateException | SecurityException failure) {
            tripStarting = false; status(R.string.real_trip_start_failed);
        }
    }
    private void renderTrip() {
        if (!active || tripStore == null) return;
        boolean running = recording();
        findViewById(R.id.real_trip_start).setEnabled(!busy && !running && selected != null && recorder != null);
        findViewById(R.id.real_trip_stop).setEnabled(running);
        findViewById(R.id.real_trip_start).setAlpha(!busy && !running && selected != null && recorder != null ? 1f : 0.45f);
        findViewById(R.id.real_trip_stop).setAlpha(running ? 1f : 0.45f);
        findViewById(R.id.classroom_demo).setEnabled(!running && !busy);
        setBusy(busy);
        if (recorder != null && owner.equals(recorder.owner())) {
            ((TextView) findViewById(R.id.real_trip_status)).setText(recorder.message());
            TripRecord trip = recorder.current();
            ((TextView) findViewById(R.id.real_trip_summary)).setText(trip == null ? "" : TripText.summary(this, trip));
            VehicleReading sample = recorder.latest();
            if (sample != null && sample != displayedTripSample && sample.recordedAt >= lastDisplayedAt) {
                DashboardRenderer.renderLatest(findViewById(R.id.root), sample);
                displayedTripSample = sample; lastDisplayedAt = sample.recordedAt;
            }
        }
        try { ((Button) findViewById(R.id.real_trip_history)).setText(getString(R.string.real_trip_history_count, tripStore.load().size())); }
        catch (IllegalStateException failure) { status(R.string.storage_failed); }
    }
    private void showTrips() {
        if (!active) return;
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        rows.setPadding(padding, padding, padding, padding); scroll.addView(rows);
        TextView note = new TextView(this); note.setTextAppearance(R.style.DashboardText);
        note.setText(R.string.real_trip_history_note); rows.addView(note);
        try {
            List<TripRecord> records = tripStore.load();
            if (records.isEmpty()) { TextView empty = new TextView(this); empty.setText(R.string.real_trip_empty); rows.addView(empty); }
            for (TripRecord record : records) {
                TextView row = new TextView(this); row.setTextAppearance(R.style.DashboardText);
                row.setBackgroundResource(R.drawable.surface_card); row.setText(TripText.summary(this, record));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = padding;
                rows.addView(row, params);
            }
        } catch (IllegalStateException failure) { status(R.string.storage_failed); return; }
        picker = new AlertDialog.Builder(this).setTitle(R.string.real_trip_history).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void status(int message) {
        ((TextView) findViewById(R.id.connection_status)).setText(message);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CONNECT_PERMISSION && active) {
            if (permitted()) chooseReader();
            else status(R.string.bluetooth_permission);
        }
    }

    @Override protected void onStop() {
        active = false;
        ui.removeCallbacksAndMessages(null);
        if (bound) { unbindService(serviceConnection); bound = false; }
        recorder = null; tripStarting = false;
        boolean wasBusy = busy;
        cancelReading();
        if (wasBusy) status(R.string.read_cancelled);
        if (picker != null) { picker.dismiss(); picker = null; }
        findViewById(R.id.root).setVisibility(View.INVISIBLE);
        if (auth != null) auth.removeAuthStateListener(listener);
        super.onStop();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void openLogin() {
        if (navigating) return;
        navigating = true;
        cancelReading();
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
