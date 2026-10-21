package com.carmind.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One atomic local document per Firebase UID. Real trips never contain demo data. */
final class TripStore {
    static final String FILE = "carmind_real_trips_v1";
    private final SharedPreferences preferences;
    private final String owner;
    TripStore(Context context, String owner) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("Account required");
        preferences = context.getSharedPreferences(FILE, 0); this.owner = owner;
    }
    private JSONObject state() {
        try { return new JSONObject(preferences.getString(owner, "{}")); }
        catch (JSONException invalid) { throw new IllegalStateException("Trip storage cannot be read", invalid); }
    }
    synchronized List<TripRecord> load() {
        List<TripRecord> result = new ArrayList<>();
        JSONArray values = state().optJSONArray("history");
        if (values != null) for (int i = 0; i < Math.min(values.length(), 100); i++) {
            try { result.add(decode(values.getJSONObject(i))); }
            catch (JSONException | IllegalArgumentException invalid) { throw new IllegalStateException("Invalid saved trip", invalid); }
        }
        return result;
    }
    synchronized TripRecord begin(long at) {
        synchronized (TripStore.class) {
            JSONObject state = state();
            if (state.has("draft")) throw new IllegalStateException("Trip already recording");
            int number = state.optInt("next", 1);
            TripRecord trip = new TripRecord(number, at);
            try { state.put("next", number + 1).put("draft", encode(trip)); }
            catch (JSONException invalid) { throw new IllegalStateException(invalid); }
            persist(state); return trip;
        }
    }
    synchronized void save(TripRecord trip) {
        synchronized (TripStore.class) {
            JSONObject state = state();
            JSONObject current = state.optJSONObject("draft");
            if (current == null || !trip.id.equals(current.optString("id"))) throw new IllegalStateException("Trip changed");
            try { state.put("draft", encode(trip)); }
            catch (JSONException invalid) { throw new IllegalStateException(invalid); }
            persist(state);
        }
    }
    synchronized void finish(TripRecord trip) {
        synchronized (TripStore.class) {
            if (trip.reason.equals("recording")) throw new IllegalArgumentException("End trip first");
            JSONObject state = state();
            JSONArray before = state.optJSONArray("history"), after = new JSONArray();
            if (trip.samples > 0) after.put(encode(trip));
            if (before != null) for (int i = 0; i < before.length() && after.length() < 100; i++) {
                JSONObject item = before.optJSONObject(i);
                if (item != null && !trip.id.equals(item.optString("id"))) after.put(item);
            }
            JSONObject draft = state.optJSONObject("draft");
            if (draft != null && trip.id.equals(draft.optString("id"))) state.remove("draft");
            try { state.put("history", after); }
            catch (JSONException invalid) { throw new IllegalStateException(invalid); }
            persist(state);
        }
    }
    synchronized void recover() {
        synchronized (TripStore.class) {
            JSONObject draft = state().optJSONObject("draft");
            if (draft == null) return;
            try {
                TripRecord trip = decode(draft);
                trip.finish("interrupted", trip.durationMs);
                finish(trip);
            } catch (JSONException | IllegalArgumentException invalid) { throw new IllegalStateException("Invalid draft", invalid); }
        }
    }
    private void persist(JSONObject state) {
        if (!preferences.edit().putString(owner, state.toString()).commit()) throw new IllegalStateException("Trip storage failed");
    }
    static JSONObject encode(TripRecord t) {
        try {
            return new JSONObject().put("id", t.id).put("number", t.number).put("start", t.startedAt)
                    .put("duration", t.durationMs).put("coverage", t.speedCoverageMs).put("samples", t.samples)
                    .put("speedN", t.speedSamples).put("rpmN", t.rpmSamples).put("coolantN", t.coolantSamples)
                    .put("speedSum", t.speedSum).put("rpmSum", t.rpmSum).put("coolantSum", t.coolantSum)
                    .put("maxSpeed", t.maxSpeed).put("maxRpm", t.maxRpm).put("maxCoolant", t.maxCoolant)
                    .put("km", t.kilometers).put("reason", t.reason);
        } catch (JSONException invalid) { throw new IllegalArgumentException("Invalid trip values", invalid); }
    }
    static TripRecord decode(JSONObject o) throws JSONException {
        TripRecord t = new TripRecord(o.getString("id"), o.getInt("number"), o.getLong("start"));
        t.durationMs = o.getLong("duration"); t.speedCoverageMs = o.getLong("coverage");
        t.samples = o.getInt("samples"); t.speedSamples = o.getInt("speedN"); t.rpmSamples = o.getInt("rpmN");
        t.coolantSamples = o.getInt("coolantN"); t.speedSum = o.getDouble("speedSum");
        t.rpmSum = o.getDouble("rpmSum"); t.coolantSum = o.getDouble("coolantSum");
        t.maxSpeed = o.getDouble("maxSpeed"); t.maxRpm = o.getDouble("maxRpm"); t.maxCoolant = o.getDouble("maxCoolant");
        t.kilometers = o.getDouble("km"); t.reason = o.getString("reason");
        if (t.durationMs < 0 || t.speedCoverageMs < 0 || t.speedCoverageMs > t.durationMs || t.samples < 0
                || t.speedSamples < 0 || t.speedSamples > t.samples || t.rpmSamples < 0 || t.rpmSamples > t.samples
                || t.coolantSamples < 0 || t.coolantSamples > t.samples || !Double.isFinite(t.kilometers) || t.kilometers < 0)
            throw new IllegalArgumentException("Invalid trip totals");
        return t;
    }
}
