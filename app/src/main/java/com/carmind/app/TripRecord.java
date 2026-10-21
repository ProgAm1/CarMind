package com.carmind.app;

import java.util.UUID;

/** A real trip summary. No positions, routes, or generated readings. */
final class TripRecord {
    final String id;
    final int number;
    final long startedAt;
    long durationMs, speedCoverageMs;
    int samples, speedSamples, rpmSamples, coolantSamples;
    double speedSum, rpmSum, coolantSum, maxSpeed, maxRpm, maxCoolant = -40, kilometers;
    String reason = "recording";
    private Double previousSpeed;
    private long previousElapsed;

    TripRecord(int number, long startedAt) { this(UUID.randomUUID().toString(), number, startedAt); }
    TripRecord(String id, int number, long startedAt) {
        if (id == null || id.isEmpty() || number < 1 || startedAt <= 0) throw new IllegalArgumentException("Invalid trip");
        this.id = id; this.number = number; this.startedAt = startedAt;
    }
    void add(VehicleReading reading, long elapsed) {
        if (!reason.equals("recording") || elapsed < durationMs || elapsed < 0) throw new IllegalArgumentException("Out of order sample");
        long gap = elapsed - previousElapsed;
        // Never extrapolate across a missing speed, dropped connection or a long response gap.
        if (samples > 0 && previousSpeed != null && reading.speed != null && gap > 0 && gap <= 15000) {
            kilometers += (previousSpeed + reading.speed) / 2 * gap / 3600000;
            speedCoverageMs += gap;
        }
        durationMs = elapsed; previousElapsed = elapsed; previousSpeed = reading.speed;
        samples++;
        if (reading.speed != null) { speedSamples++; speedSum += reading.speed; maxSpeed = Math.max(maxSpeed, reading.speed); }
        if (reading.rpm != null) { rpmSamples++; rpmSum += reading.rpm; maxRpm = Math.max(maxRpm, reading.rpm); }
        if (reading.coolant != null) { coolantSamples++; coolantSum += reading.coolant; maxCoolant = Math.max(maxCoolant, reading.coolant); }
    }
    long endedAt() { return startedAt + durationMs; }
    void finish(String reason, long elapsed) {
        if (!this.reason.equals("recording")) return;
        this.reason = reason;
        durationMs = Math.max(durationMs, elapsed);
    }
}
