package com.carmind.app;

/** Deterministic teaching model, not vehicle measurements or a vehicle physics model. */
final class DriveSimulation {
    enum Stage { UNPLUGGED, POWERED, PAIRING, NEGOTIATING, CONNECTED, DISCONNECTED }
    enum Mode { IDLE, CITY, HIGHWAY }
    Stage stage = Stage.UNPLUGGED;
    Mode mode = Mode.CITY;
    boolean paused;
    int seconds;
    double speed, rpm = 800, coolant = 76;
    double profilePhase;

    void plugIn() { if (stage == Stage.UNPLUGGED) stage = Stage.POWERED; }
    void pair() {
        if (stage == Stage.POWERED || stage == Stage.DISCONNECTED) stage = Stage.PAIRING;
    }
    void advanceConnection() {
        if (stage == Stage.PAIRING) stage = Stage.NEGOTIATING;
        else if (stage == Stage.NEGOTIATING) { stage = Stage.CONNECTED; paused = false; }
    }
    void disconnect() { if (stage != Stage.UNPLUGGED) { stage = Stage.DISCONNECTED; paused = true; } }
    void background() {
        paused = true;
        if (stage == Stage.PAIRING || stage == Stage.NEGOTIATING) stage = Stage.POWERED;
    }
    boolean tick() {
        if (stage != Stage.CONNECTED || paused) return false;
        seconds++;
        double target = mode == Mode.IDLE ? 0 : mode == Mode.HIGHWAY
                ? 92 + 5 * Math.sin(seconds / 8.0 + profilePhase) : 28 + 20 * Math.sin(seconds / 10.0 + profilePhase);
        speed += Math.max(-4, Math.min(3, target - speed));
        rpm = speed < 1 ? 800 + 20 * Math.sin(seconds + profilePhase) : 950 + speed * 22 + 90 * Math.sin(seconds / 3.0 + profilePhase);
        coolant = Math.min(90, 76 + seconds * 0.12) + 0.3 * Math.sin(seconds / 15.0 + profilePhase);
        return true;
    }
    VehicleReading reading(long at) {
        if (stage != Stage.CONNECTED) throw new IllegalStateException("No simulated connection");
        return new VehicleReading(at, "SIMULATED · Classroom OBD", (double) Math.round(rpm),
                (double) Math.round(speed), (double) Math.round(coolant));
    }
}
