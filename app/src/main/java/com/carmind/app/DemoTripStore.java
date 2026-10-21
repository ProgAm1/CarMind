package com.carmind.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small local demo records, partitioned by the authenticated UID. No location collection or cloud writes. */
final class DemoTripStore {
    static final String FILE = "carmind_demo_trips_v1";
    private final SharedPreferences preferences;
    private final String key;

    DemoTripStore(Context context, String owner) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("Account required");
        key = owner;
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    void initializeExamples(long now) {
        synchronized (DemoTripStore.class) {
            if (preferences.getBoolean(key + ":initialized", false)) return;
            preferences.edit().putString(key + ":history", encode(DemoTrip.examples(key, now)))
                    .putBoolean(key + ":initialized", true).apply();
        }
    }
    List<DemoTrip> load() {
        List<DemoTrip> trips = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(key + ":history", "[]"));
            // Persist numbering once when upgrading the older route-based demo records.
            boolean migrated = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null && !item.has("number")) { item.put("number", array.length() - i); item.remove("route"); migrated = true; }
            }
            if (migrated) preferences.edit().putString(key + ":history", array.toString()).apply();
            for (int i = 0; i < Math.min(20, array.length()); i++) {
                try {
                    DemoTrip trip = fromJson(array.getJSONObject(i), array.length() - i);
                    if (trip.ended) trips.add(trip);
                } catch (JSONException | IllegalArgumentException ignored) { /* Skip malformed records. */ }
            }
        } catch (JSONException ignored) { /* Never invent replacements for damaged saved records. */ }
        trips.sort(Comparator.comparingLong(DemoTrip::time).reversed());
        return trips;
    }
    DemoTrip draft() {
        String raw = preferences.getString(key + ":draft", null);
        if (raw == null) return null;
        try {
            DemoTrip trip = fromJson(new JSONObject(raw), nextNumber());
            return trip.ended ? null : trip;
        } catch (JSONException | IllegalArgumentException ignored) { return null; }
    }
    int nextNumber() {
        int next = preferences.getInt(key + ":next", 1);
        for (DemoTrip saved : load()) next = Math.max(next, saved.number + 1);
        return next;
    }
    void saveDraft(DemoTrip trip) {
        if (trip.ended) throw new IllegalArgumentException("Use finish for completed trips");
        preferences.edit().putString(key + ":draft", toJson(trip).toString())
                .putInt(key + ":next", Math.max(nextNumber(), trip.number + 1)).apply();
    }
    void discardEmptyDraft() {
        DemoTrip trip = draft();
        if (trip == null || trip.seconds == 0) preferences.edit().remove(key + ":draft").apply();
    }
    void finish(DemoTrip trip) {
        if (!trip.ended || trip.seconds == 0) throw new IllegalArgumentException("Trip must be ended");
        synchronized (DemoTripStore.class) {
            List<DemoTrip> trips = load();
            trips.removeIf(existing -> existing.id.equals(trip.id));
            trips.add(trip);
            trips.sort(Comparator.comparingLong(DemoTrip::time).reversed());
            if (trips.size() > 20) trips = new ArrayList<>(trips.subList(0, 20));
            SharedPreferences.Editor editor = preferences.edit().putString(key + ":history", encode(trips))
                    .putInt(key + ":next", Math.max(nextNumber(), trip.number + 1));
            DemoTrip draft = draft();
            if (draft != null && draft.id.equals(trip.id)) editor.remove(key + ":draft");
            editor.apply();
        }
    }
    private static String encode(List<DemoTrip> trips) {
        JSONArray array = new JSONArray();
        for (DemoTrip trip : trips) array.put(toJson(trip));
        return array.toString();
    }
    private static JSONObject toJson(DemoTrip trip) {
        try {
            return new JSONObject().put("id", trip.id).put("number", trip.number).put("start", trip.startedAt)
                    .put("seconds", trip.seconds).put("km", trip.kilometers).put("max", trip.maxSpeed).put("ended", trip.ended);
        } catch (JSONException impossible) { throw new IllegalArgumentException("Invalid trip", impossible); }
    }
    private static DemoTrip fromJson(JSONObject value, int fallbackNumber) throws JSONException {
        return new DemoTrip(value.getString("id"), value.optInt("number", fallbackNumber), value.getLong("start"),
                value.getInt("seconds"), value.getDouble("km"), value.getDouble("max"), value.getBoolean("ended"));
    }
}
