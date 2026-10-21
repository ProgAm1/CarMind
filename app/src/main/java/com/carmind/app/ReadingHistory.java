package com.carmind.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Bounded, local history, separated by the signed-in Firebase UID. No cloud writes. */
final class ReadingHistory {
    private final SharedPreferences preferences;
    private final String owner;

    ReadingHistory(Context context, String owner) {
        this(context, owner, "carmind_readings_v1");
    }

    static ReadingHistory simulation(Context context, String owner) {
        return new ReadingHistory(context, owner, "carmind_demo_readings_v1");
    }

    private ReadingHistory(Context context, String owner, String store) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("User required");
        this.owner = owner;
        preferences = context.getSharedPreferences(store, Context.MODE_PRIVATE);
    }

    List<VehicleReading> load() {
        List<VehicleReading> readings = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(owner, "[]"));
            for (int i = 0; i < Math.min(array.length(), 20); i++) {
                try {
                    JSONObject object = array.getJSONObject(i);
                    readings.add(new VehicleReading(object.getLong("at"), object.getString("device"),
                            number(object, "rpm"), number(object, "speed"), number(object, "coolant")));
                } catch (JSONException | IllegalArgumentException ignored) { /* Skip a damaged record. */ }
            }
        } catch (JSONException ignored) { /* Unreadable history is treated as empty. */ }
        return readings;
    }

    // Called on the worker thread, so commit can report a storage failure.
    boolean save(VehicleReading reading) {
        synchronized (ReadingHistory.class) { return saveLocked(reading); }
    }

    private boolean saveLocked(VehicleReading reading) {
        List<VehicleReading> readings = load();
        readings.add(0, reading);
        JSONArray array = new JSONArray();
        try {
            for (VehicleReading item : readings.subList(0, Math.min(20, readings.size()))) {
                JSONObject object = new JSONObject();
                object.put("at", item.recordedAt);
                object.put("device", item.deviceName);
                object.put("rpm", item.rpm == null ? JSONObject.NULL : item.rpm);
                object.put("speed", item.speed == null ? JSONObject.NULL : item.speed);
                object.put("coolant", item.coolant == null ? JSONObject.NULL : item.coolant);
                array.put(object);
            }
        } catch (JSONException invalid) { return false; }
        return preferences.edit().putString(owner, array.toString()).commit();
    }

    private static Double number(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? null : object.getDouble(key);
    }
}
