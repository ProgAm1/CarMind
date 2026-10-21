package com.carmind.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Isolated renderer host for instrumentation. Never connects to a car or Firebase. */
@android.annotation.SuppressLint("SetTextI18n") // Fixed English evidence labels in the test host only.
public class DashboardPreviewActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(getColor(R.color.canvas));
        TextView banner = new TextView(this);
        banner.setText("UI TEST PREVIEW • SIMULATED DATA");
        banner.setTextSize(12);
        banner.setTextColor(getColor(R.color.surface));
        banner.setBackgroundColor(getColor(R.color.ink));
        banner.setPadding(24, 12, 24, 12);
        wrapper.addView(banner, new LinearLayout.LayoutParams(-1, -2));
        View root = getLayoutInflater().inflate(R.layout.activity_home, wrapper, false);
        wrapper.addView(root, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(wrapper);
        ScreenInsets.apply(wrapper);
        ((TextView) findViewById(R.id.account_email)).setText("demo@example.com");
        List<VehicleReading> readings = new ArrayList<>();
        if (!getIntent().getBooleanExtra("empty", false)) {
            long timestamp = 1792494000000L;
            readings.add(new VehicleReading(timestamp, "Sample OBD reader", 1726.0, 0.0, 90.0));
            readings.add(new VehicleReading(timestamp - 300000, "Sample OBD reader", 850.0, 0.0, 82.0));
            readings.add(new VehicleReading(timestamp - 600000, "Sample OBD reader", null, 0.0, 65.0));
            ((TextView) findViewById(R.id.reader_name)).setText("Sample OBD reader");
            ((TextView) findViewById(R.id.connection_status)).setText("UI preview. No vehicle connected.");
        }
        DashboardRenderer.render(root, readings);
        if (getIntent().getBooleanExtra("trip", false)) {
            TripRecord trip = new TripRecord(3, 1792566000000L);
            trip.add(new VehicleReading(trip.startedAt, "TEST", 1500.0, 40.0, 85.0), 0);
            trip.add(new VehicleReading(trip.startedAt + 10000, "TEST", 1800.0, 50.0, 88.0), 10000);
            trip.finish("finished", 12000);
            ((TextView) findViewById(R.id.real_trip_summary)).setText(TripText.summary(this, trip));
            ((TextView) findViewById(R.id.real_trip_status)).setText("UI preview only • recorded trip layout");
        }
        for (int id : new int[]{R.id.choose_reader, R.id.bluetooth_settings, R.id.read_vehicle, R.id.sign_out}) {
            findViewById(id).setEnabled(false);
        }
    }
}
