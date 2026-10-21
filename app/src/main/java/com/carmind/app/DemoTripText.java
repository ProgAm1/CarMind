package com.carmind.app;

import android.content.Context;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class DemoTripText {
    static String time(long value) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
        return format.format(new Date(value));
    }
    static String duration(int seconds) {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    static String summary(Context context, DemoTrip trip) {
        return context.getString(R.string.trip_record, trip.number,
                time(trip.startedAt), time(trip.time()), duration(trip.seconds), trip.kilometers,
                trip.averageSpeed(), trip.maxSpeed);
    }
}
