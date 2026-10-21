package com.carmind.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A labelled classroom simulation behind the same Firebase session as the real dashboard. */
public class SimulationActivity extends Activity {
    private FirebaseAuth auth;
    private String owner;
    private boolean active, navigating, saving;
    private AlertDialog picker;
    private ReadingHistory history;
    private DemoTripStore trips;
    private DemoTrip trip;
    private DriveSimulation drive = new DriveSimulation();
    private VehicleReading latest;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private final FirebaseAuth.AuthStateListener authListener = value -> {
        if (!validSession()) openLogin();
    };
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (!active || !validSession()) return;
            if (drive.tick()) {
                latest = drive.reading(System.currentTimeMillis());
                renderValues();
                if (trip != null) {
                    trip.tick(drive.speed);
                    if (trip.ended) finishTrip();
                    else { trips.saveDraft(trip); renderTrip(); }
                }
                if (drive.seconds % 5 == 0) saveReading();
            }
            handler.postDelayed(this, 1000);
        }
    };
    private final Runnable connection = new Runnable() {
        @Override public void run() {
            if (!active || !validSession()) return;
            drive.advanceConnection();
            render();
            if (drive.stage == DriveSimulation.Stage.NEGOTIATING) handler.postDelayed(this, 1800);
            else if (drive.stage == DriveSimulation.Stage.CONNECTED) {
                latest = drive.reading(System.currentTimeMillis());
                renderValues();
                saveReading();
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_simulation);
        ScreenInsets.apply(findViewById(R.id.demo_root));
        auth = ((CarMindApplication) getApplication()).getAuth();
        if (state != null) {
            owner = state.getString("owner");
            drive.stage = DriveSimulation.Stage.values()[state.getInt("stage")];
            drive.mode = DriveSimulation.Mode.values()[state.getInt("mode")];
            drive.seconds = state.getInt("seconds");
            drive.speed = state.getDouble("speed");
            drive.rpm = state.getDouble("rpm");
            drive.coolant = state.getDouble("coolant");
            drive.background();
            long at = state.getLong("lastAt");
            if (at > 0) latest = new VehicleReading(at, "SIMULATED · Classroom OBD",
                    (double) Math.round(drive.rpm), (double) Math.round(drive.speed), (double) Math.round(drive.coolant));
        }
        ((RadioGroup) findViewById(R.id.demo_modes)).check(drive.mode == DriveSimulation.Mode.IDLE
                ? R.id.demo_idle : drive.mode == DriveSimulation.Mode.HIGHWAY ? R.id.demo_highway : R.id.demo_city);
        ((RadioGroup) findViewById(R.id.demo_modes)).setOnCheckedChangeListener((group, id) -> {
            drive.mode = id == R.id.demo_idle ? DriveSimulation.Mode.IDLE
                    : id == R.id.demo_highway ? DriveSimulation.Mode.HIGHWAY : DriveSimulation.Mode.CITY;
            renderStatus();
        });
        findViewById(R.id.trip_start).setOnClickListener(view -> startTrip());
        findViewById(R.id.trip_end).setOnClickListener(view -> finishTrip());
        findViewById(R.id.trip_history_button).setOnClickListener(view -> showTrips());
        findViewById(R.id.demo_connect).setOnClickListener(view -> connect());
        findViewById(R.id.demo_pause).setOnClickListener(view -> {
            drive.paused = !drive.paused;
            render();
        });
        findViewById(R.id.demo_disconnect).setOnClickListener(view -> {
            handler.removeCallbacks(connection);
            drive.disconnect();
            render();
        });
        findViewById(R.id.demo_save).setOnClickListener(view -> saveReading());
        findViewById(R.id.demo_explain).setOnClickListener(view -> picker = new AlertDialog.Builder(this)
                .setTitle(R.string.demo_flow_title).setMessage(R.string.demo_explanation)
                .setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.demo_back).setOnClickListener(view -> finish());
        findViewById(R.id.demo_sign_out).setOnClickListener(view -> {
            handler.removeCallbacksAndMessages(null);
            if (auth != null) auth.signOut();
            openLogin();
        });
        findViewById(R.id.demo_restart).setOnClickListener(view -> {
            finishTrip();
            handler.removeCallbacks(connection);
            drive = new DriveSimulation();
            drive.profilePhase = Math.floorMod(DemoTrip.accountSeed(owner), 360) / 30.0;
            latest = null;
            ((RadioGroup) findViewById(R.id.demo_modes)).check(R.id.demo_city);
            render();
        });
        findViewById(R.id.demo_content).setVisibility(View.INVISIBLE);
    }

    // Overridden only by the non-exported debug component-test host, never by an Intent flag.
    protected String currentOwner() {
        return auth == null || auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }
    protected String currentEmail() {
        return auth == null || auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getEmail();
    }
    private boolean validSession() { return owner != null && owner.equals(currentOwner()); }

    @Override protected void onStart() {
        super.onStart();
        String uid = currentOwner();
        if (uid == null || (owner != null && !owner.equals(uid))) { openLogin(); return; }
        owner = uid;
        drive.profilePhase = Math.floorMod(DemoTrip.accountSeed(owner), 360) / 30.0;
        history = ReadingHistory.simulation(this, owner);
        trips = new DemoTripStore(this, owner);
        trips.initializeExamples(System.currentTimeMillis());
        trip = trips.draft();
        active = true;
        if (auth != null) auth.addAuthStateListener(authListener);
        text(R.id.demo_account, currentEmail());
        findViewById(R.id.demo_content).setVisibility(View.VISIBLE);
        render();
        renderHistory();
        handler.postDelayed(clock, 1000);
    }

    private void connect() {
        if (!active || !validSession()) return;
        if (drive.stage == DriveSimulation.Stage.UNPLUGGED) {
            drive.plugIn();
            render();
        } else if (drive.stage == DriveSimulation.Stage.POWERED || drive.stage == DriveSimulation.Stage.DISCONNECTED) {
            picker = new AlertDialog.Builder(this).setTitle(R.string.demo_picker)
                    .setItems(new String[]{getString(R.string.demo_reader)}, (dialog, which) -> {
                        drive.pair();
                        render();
                        handler.postDelayed(connection, 1800);
                    }).setNegativeButton(R.string.cancel, null).show();
        }
    }

    private void render() {
        boolean connected = drive.stage == DriveSimulation.Stage.CONNECTED;
        boolean connecting = drive.stage == DriveSimulation.Stage.PAIRING || drive.stage == DriveSimulation.Stage.NEGOTIATING;
        text(R.id.demo_step_power, getString(drive.stage == DriveSimulation.Stage.UNPLUGGED ? R.string.demo_power_wait : R.string.demo_power_done));
        text(R.id.demo_step_bluetooth, getString(connected || drive.stage == DriveSimulation.Stage.NEGOTIATING
                ? R.string.demo_pair_done : R.string.demo_pair_wait));
        text(R.id.demo_step_read, getString(connected ? R.string.demo_read_done : R.string.demo_read_wait));
        findViewById(R.id.demo_steps).setVisibility(connected ? View.GONE : View.VISIBLE);
        findViewById(R.id.demo_connect).setVisibility(connected ? View.GONE : View.VISIBLE);
        findViewById(R.id.demo_connect).setEnabled(!connecting);
        ((Button) findViewById(R.id.demo_connect)).setText(drive.stage == DriveSimulation.Stage.UNPLUGGED
                ? R.string.demo_plug : drive.stage == DriveSimulation.Stage.DISCONNECTED
                ? R.string.demo_reconnect : connecting ? R.string.demo_connecting : R.string.demo_choose);
        findViewById(R.id.demo_connection_progress).setVisibility(connecting ? View.VISIBLE : View.GONE);
        findViewById(R.id.demo_pause).setEnabled(connected);
        findViewById(R.id.demo_save).setEnabled(connected && latest != null && !saving);
        findViewById(R.id.demo_disconnect).setEnabled(connected || connecting);
        ((Button) findViewById(R.id.demo_pause)).setText(drive.paused ? R.string.demo_resume : R.string.demo_pause);
        for (int id : new int[]{R.id.demo_idle, R.id.demo_city, R.id.demo_highway}) findViewById(id).setEnabled(connected);
        renderValues();
        renderStatus();
        renderTrip();
    }

    private void renderStatus() {
        int message;
        switch (drive.stage) {
            case UNPLUGGED: message = R.string.demo_status_unplugged; break;
            case POWERED: message = R.string.demo_status_powered; break;
            case PAIRING: message = R.string.demo_status_pairing; break;
            case NEGOTIATING: message = R.string.demo_status_negotiating; break;
            case DISCONNECTED: message = R.string.demo_status_disconnected; break;
            default: message = drive.paused ? R.string.demo_status_paused : R.string.demo_status_live;
        }
        text(R.id.demo_status, getString(message));
        text(R.id.demo_scenario, getString(drive.mode == DriveSimulation.Mode.IDLE ? R.string.demo_idle_note
                : drive.mode == DriveSimulation.Mode.HIGHWAY ? R.string.demo_highway_note : R.string.demo_city_note));
    }

    private void renderValues() {
        text(R.id.demo_speed, latest == null ? getString(R.string.demo_no_value) : String.valueOf(latest.speed.intValue()));
        text(R.id.demo_rpm, latest == null ? getString(R.string.demo_no_value) : String.valueOf(latest.rpm.intValue()));
        text(R.id.demo_coolant, latest == null ? getString(R.string.demo_no_value) : String.valueOf(latest.coolant.intValue()));
        text(R.id.demo_updated, latest == null ? getString(R.string.demo_no_data)
                : getString(R.string.demo_updated, DateFormat.getTimeInstance().format(new Date(latest.recordedAt))));
        findViewById(R.id.demo_save).setEnabled(drive.stage == DriveSimulation.Stage.CONNECTED && latest != null && !saving);
    }

    private void saveReading() {
        if (saving || latest == null || drive.stage != DriveSimulation.Stage.CONNECTED || !validSession()) return;
        saving = true;
        findViewById(R.id.demo_save).setEnabled(false);
        final ReadingHistory target = history;
        final VehicleReading reading = latest;
        storage.execute(() -> {
            boolean saved = target.save(reading);
            handler.post(() -> {
                saving = false;
                if (!active || !validSession()) return;
                text(R.id.demo_saved, getString(saved ? R.string.demo_saved : R.string.storage_failed));
                renderValues();
                renderHistory();
            });
        });
    }

    private void startTrip() {
        if (!active || !validSession() || trip != null || drive.stage != DriveSimulation.Stage.CONNECTED) return;
        long start = System.currentTimeMillis();
        List<DemoTrip> saved = trips.load();
        if (!saved.isEmpty()) start = Math.max(start, saved.get(0).time() + 300000);
        trip = new DemoTrip(trips.nextNumber(), start);
        trips.saveDraft(trip);
        drive.paused = false;
        text(R.id.trip_notice, getString(R.string.trip_running));
        render();
    }

    private void finishTrip() {
        if (trip == null || !validSession()) return;
        if (!trip.finish()) {
            trips.discardEmptyDraft();
            text(R.id.trip_notice, getString(R.string.trip_empty_cancelled));
        } else {
            trips.finish(trip);
            text(R.id.trip_notice, getString(R.string.trip_finished_notice));
        }
        trip = null;
        drive.paused = true;
        render();
    }

    private void renderTrip() {
        text(R.id.trip_number, getString(R.string.trip_number, trip == null ? (trips == null ? 1 : trips.nextNumber()) : trip.number));
        text(R.id.trip_history_button, getString(R.string.trip_history_button, trips == null ? 0 : trips.load().size()));
        text(R.id.trip_live, trip == null ? getString(R.string.trip_ready) : getString(R.string.trip_live,
                DemoTripText.time(trip.startedAt), DemoTripText.time(trip.time()), DemoTripText.duration(trip.seconds),
                trip.kilometers, trip.averageSpeed(), trip.maxSpeed));
        findViewById(R.id.trip_start).setVisibility(trip == null ? View.VISIBLE : View.GONE);
        findViewById(R.id.trip_start).setEnabled(trip == null && drive.stage == DriveSimulation.Stage.CONNECTED);
        findViewById(R.id.trip_start).setAlpha(drive.stage == DriveSimulation.Stage.CONNECTED ? 1f : 0.45f);
        findViewById(R.id.trip_end).setVisibility(trip != null ? View.VISIBLE : View.GONE);
        findViewById(R.id.trip_end).setEnabled(trip != null && trip.seconds > 0);
        findViewById(R.id.trip_end).setAlpha(trip != null && trip.seconds > 0 ? 1f : 0.45f);
    }

    private void showTrips() {
        if (!active || !validSession()) return;
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int spacing = (int) (16 * getResources().getDisplayMetrics().density);
        rows.setPadding(spacing, spacing, spacing, spacing);
        scroll.addView(rows);
        TextView scope = new TextView(this);
        scope.setTextAppearance(R.style.DashboardText);
        scope.setText(getString(R.string.trip_history_scope, currentEmail()));
        rows.addView(scope);
        for (DemoTrip saved : trips.load()) {
            TextView row = new TextView(this);
            row.setTextAppearance(R.style.DashboardText);
            row.setBackgroundResource(R.drawable.surface_card);
            row.setText(DemoTripText.summary(this, saved));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = spacing;
            rows.addView(row, params);
        }
        picker = new AlertDialog.Builder(this).setTitle(R.string.trip_history_title).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void renderHistory() {
        LinearLayout rows = findViewById(R.id.demo_history);
        rows.removeAllViews();
        List<VehicleReading> readings = history.load();
        text(R.id.demo_history_count, getString(R.string.demo_history_count, readings.size()));
        for (VehicleReading reading : readings) {
            TextView row = new TextView(this);
            row.setTextAppearance(R.style.DashboardText);
            row.setBackgroundResource(R.drawable.surface_card);
            row.setText(getString(R.string.demo_history_row,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(reading.recordedAt)),
                    reading.speed.intValue(), reading.rpm.intValue(), reading.coolant.intValue()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            rows.addView(row, params);
        }
    }
    private void text(int id, String value) { ((TextView) findViewById(id)).setText(value); }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("owner", owner);
        state.putInt("stage", drive.stage.ordinal());
        state.putInt("mode", drive.mode.ordinal());
        state.putInt("seconds", drive.seconds);
        state.putDouble("speed", drive.speed);
        state.putDouble("rpm", drive.rpm);
        state.putDouble("coolant", drive.coolant);
        state.putLong("lastAt", latest == null ? 0 : latest.recordedAt);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() {
        active = false;
        saving = false;
        drive.background();
        handler.removeCallbacksAndMessages(null);
        if (picker != null) { picker.dismiss(); picker = null; }
        findViewById(R.id.demo_content).setVisibility(View.INVISIBLE);
        if (auth != null) auth.removeAuthStateListener(authListener);
        super.onStop();
    }
    @Override protected void onDestroy() { storage.shutdown(); super.onDestroy(); }
    private void openLogin() {
        if (navigating) return;
        navigating = true;
        handler.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
