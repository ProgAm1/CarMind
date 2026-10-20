package com.carmind.app;

final class VehicleReading {
    final long recordedAt;
    final String deviceName;
    final Double rpm, speed, coolant;

    VehicleReading(long recordedAt, String deviceName, Double rpm, Double speed, Double coolant) {
        if (recordedAt <= 0 || deviceName == null || deviceName.trim().isEmpty()
                || (rpm == null && speed == null && coolant == null)
                || !valid(rpm, 0, 16383.75) || !valid(speed, 0, 255) || !valid(coolant, -40, 215)) {
            throw new IllegalArgumentException("Invalid vehicle reading");
        }
        this.recordedAt = recordedAt;
        this.deviceName = deviceName;
        this.rpm = rpm;
        this.speed = speed;
        this.coolant = coolant;
    }

    private static boolean valid(Double value, double min, double max) {
        return value == null || (!value.isNaN() && !value.isInfinite() && value >= min && value <= max);
    }
}
