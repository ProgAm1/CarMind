package com.carmind.app;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

/** Rendering shared by Home and the debug-only component test screen. */
final class DashboardRenderer {
    static void render(View root, List<VehicleReading> readings) {
        Context context = root.getContext();
        VehicleReading latest = readings.isEmpty() ? null : readings.get(0);
        renderLatest(root, latest);
        LinearLayout history = root.findViewById(R.id.history_list);
        history.removeAllViews();
        if (readings.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setTextAppearance(R.style.DashboardText);
            empty.setText(R.string.history_empty);
            history.addView(empty);
        } else {
            for (VehicleReading reading : readings) {
                TextView row = new TextView(context);
                row.setTextAppearance(R.style.DashboardText);
                row.setBackgroundResource(R.drawable.surface_card);
                row.setText(context.getString(R.string.history_entry, source(context, reading),
                        context.getString(R.string.history_metrics, number(context, reading.rpm),
                                number(context, reading.speed), number(context, reading.coolant))));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = (int) (12 * context.getResources().getDisplayMetrics().density);
                history.addView(row, params);
            }
        }
    }

    static void renderLatest(View root, VehicleReading latest) {
        Context context = root.getContext();
        text(root, R.id.rpm_value, number(context, latest == null ? null : latest.rpm));
        text(root, R.id.speed_value, number(context, latest == null ? null : latest.speed));
        text(root, R.id.coolant_value, number(context, latest == null ? null : latest.coolant));
        text(root, R.id.reading_time, latest == null ? context.getString(R.string.no_readings)
                : source(context, latest));
    }

    private static String source(Context context, VehicleReading reading) {
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                .format(new Date(reading.recordedAt));
        return context.getString(R.string.recorded_from, date, reading.deviceName);
    }

    static String number(Context context, Double value) {
        if (value == null) return context.getString(R.string.not_available);
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private static void text(View root, int id, String value) {
        ((TextView) root.findViewById(id)).setText(value);
    }
}
