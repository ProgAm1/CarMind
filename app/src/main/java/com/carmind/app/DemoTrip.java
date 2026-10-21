package com.carmind.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Numbered generated classroom journeys. No location data. */
final class DemoTrip {
    static final int SECONDS_PER_TICK = 30;
    final String id;
    final int number;
    final long startedAt;
    int seconds;
    double kilometers, maxSpeed;
    boolean ended;

    DemoTrip(int number, long startedAt) {
        this(UUID.randomUUID().toString(), number, startedAt, 0, 0, 0, false);
    }
    DemoTrip(String id, int number, long startedAt, int seconds, double kilometers, double maxSpeed, boolean ended) {
        if (id == null || id.isEmpty() || number < 1 || startedAt <= 0
                || seconds < 0 || seconds > 86400 || !Double.isFinite(kilometers) || kilometers < 0
                || !Double.isFinite(maxSpeed) || maxSpeed < 0
                || maxSpeed > 255 || (ended && seconds == 0)
                || (seconds == 0 && kilometers != 0)
                || (seconds > 0 && kilometers * 3600 / seconds > maxSpeed + 0.01)) {
            throw new IllegalArgumentException("Invalid simulated trip");
        }
        this.id = id; this.number = number; this.startedAt = startedAt;
        this.seconds = seconds; this.kilometers = kilometers; this.maxSpeed = maxSpeed; this.ended = ended;
    }
    long time() { return startedAt + seconds * 1000L; }
    double averageSpeed() { return seconds == 0 ? 0 : kilometers * 3600 / seconds; }
    void tick(double speed) {
        if (ended) return;
        if (!Double.isFinite(speed) || speed < 0 || speed > 255) throw new IllegalArgumentException("Invalid speed");
        int step = Math.min(SECONDS_PER_TICK, 86400 - seconds);
        seconds += step;
        kilometers += speed * step / 3600;
        maxSpeed = Math.max(maxSpeed, speed);
        if (seconds >= 86400) ended = true;
    }
    boolean finish() {
        if (seconds == 0) return false;
        ended = true;
        return true;
    }
    static long accountSeed(String owner) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("Account required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) seed = (seed << 8) | (digest[i] & 255);
            return seed;
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static List<DemoTrip> examples(String owner, long now) {
        Random random = new Random(accountSeed(owner));
        List<DemoTrip> trips = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int duration = (15 + random.nextInt(30)) * 60;
            double distance = (24 + random.nextInt(18)) * duration / 3600.0;
            long end = now - (i + 1) * 86400000L - random.nextInt(21600) * 1000L;
            trips.add(new DemoTrip("example-" + Long.toHexString(accountSeed(owner)) + "-" + i,
                    4 - i, end - duration * 1000L, duration, distance, 55 + random.nextInt(20), true));
        }
        return trips;
    }
}
