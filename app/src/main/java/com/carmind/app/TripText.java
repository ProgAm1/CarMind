package com.carmind.app;

import android.content.Context;
import java.util.Locale;

final class TripText {
    private static String value(double sum, int count) {
        return count == 0 ? "—" : String.format(Locale.getDefault(), "%.1f", sum / count);
    }
    static String summary(Context context, TripRecord trip) {
        int reason;
        switch (trip.reason) {
            case "recording": reason = R.string.real_trip_recording; break;
            case "finished": reason = R.string.real_trip_finished; break;
            case "disconnected": reason = R.string.real_trip_disconnected; break;
            case "signed_out": reason = R.string.real_trip_signed_out; break;
            default: reason = R.string.real_trip_interrupted;
        }
        return context.getString(R.string.real_trip_record, trip.number, DemoTripText.time(trip.startedAt),
                DemoTripText.time(trip.endedAt()), DemoTripText.duration((int) (trip.durationMs / 1000)),
                value(trip.speedSum, trip.speedSamples), value(trip.maxSpeed, trip.speedSamples > 0 ? 1 : 0),
                value(trip.rpmSum, trip.rpmSamples), value(trip.maxRpm, trip.rpmSamples > 0 ? 1 : 0),
                value(trip.coolantSum, trip.coolantSamples), value(trip.maxCoolant, trip.coolantSamples > 0 ? 1 : 0),
                trip.speedCoverageMs == 0 ? "—" : String.format(Locale.getDefault(), "%.2f", trip.kilometers),
                DemoTripText.duration((int) (trip.speedCoverageMs / 1000)), trip.samples, context.getString(reason));
    }
}
